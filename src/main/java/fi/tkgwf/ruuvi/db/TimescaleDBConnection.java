package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;
import org.apache.log4j.Logger;

public class TimescaleDBConnection implements RuuviDBConnection {

    private static final String MEASUREMENT = "measurement";
    private static final String SENSOR = "sensor";
    private static final Logger LOG = Logger.getLogger(TimescaleDBConnection.class);
    private static Configuration cfg = Configuration.get();
    private final Map<String, ConfiguredSensor> configuredSensors = new HashMap<>();
    private Connection con;
    // cached for efficiency
    private PreparedStatement writeMeasurementPS;
    private int batchCounter;

    public static void main(String[] args) throws Exception {
        new TimescaleDBConnection();
    }

    public TimescaleDBConnection() throws SQLException {
        this(cfg.timescaleDB.url, cfg.timescaleDB.user, cfg.timescaleDB.pwd);
    }

    public TimescaleDBConnection(String url, String user, String pwd) throws SQLException {

        LOG.info(
                "Configure reflection access to " + EnhancedRuuviMeasurement.class.getSimpleName());
        EnhancedRuuviMeasurement.enableCallFieldGetterByMethodName();
        // Auto-fix for minor detail.
        if (!url.endsWith("/")) {
            url += "/";
        }

        LOG.info("Connecting to database..");
        con = DriverManager.getConnection(url + cfg.timescaleDB.database, user, pwd);
        LOG.info("..connected.");
        if (cfg.timescaleDB.createTables) {
            createTables();
        }
    }

    @Override
    public void save(EnhancedRuuviMeasurement measurement) {
        try {
            writeSensorInfo(measurement);
            writeMeasurement(measurement);
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {
        try {
            con.close();
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    private void createTables() throws SQLException {
        LOG.info("Creating tables for database: " + cfg.timescaleDB.database);

        executeUpdate(getSensorTableStr());
        executeUpdate(getMeasurementTableStr());
        createHypertable();
        executeUpdate(
                "CREATE INDEX IF NOT EXISTS idx_device_id_time ON "
                        + MEASUREMENT
                        + " (device_id, time DESC)");

        LOG.info("database configured.");
    }

    private String getSensorTableStr() {
        return "CREATE TABLE IF NOT EXISTS "
                + SENSOR
                + " ("
                + " id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,"
                + " name TEXT UNIQUE,"
                + " description TEXT,"
                + " create_time TIMESTAMPTZ,"
                + " battery_change_time TIMESTAMPTZ,"
                + " mac_address TEXT UNIQUE)";
    }

    private String getMeasurementTableStr() {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + MEASUREMENT);
        sb.append(" (");
        sb.append("device_id INTEGER REFERENCES " + SENSOR + "(id) ON DELETE RESTRICT, ");
        Configuration.get().storage.fields.forEach(field -> appendField(sb, field));
        sb.append(" time TIMESTAMPTZ NOT NULL)");
        return sb.toString();
    }

    /** Hacky trick to derive datatype from field name. */
    private void appendField(StringBuilder sb, String name) {
        String dataType = " DOUBLE PRECISION";
        if (isIntField(name)) {
            dataType = " INTEGER";
        } else if (isTimeField(name)) {
            dataType = " TIMESTAMPTZ NOT NULL";
        }
        sb.append(toSnakeCase(name)).append(dataType).append(",");
    }

    private boolean isIntField(String name) {
        return name.endsWith("Number") || name.endsWith("Counter");
    }

    private boolean isTimeField(String name) {
        return name.endsWith("Time");
    }

    private void writeMeasurement(EnhancedRuuviMeasurement measurement) throws SQLException {
        initWritePS();
        int idx = 1;
        // Manage preparedStatement values
        writeMeasurementPS.setInt(idx++, configuredSensors.get(measurement.getMac()).id);
        for (var name : cfg.storage.fields) {
            var value = measurement.getFieldValue(name);
            // LOG.info("Field: " + name + " Value: " + value);
            var sqlType = Types.DOUBLE;
            if (isIntField(name)) {
                sqlType = Types.INTEGER;
            } else if (isTimeField(name)) {
                sqlType = Types.TIMESTAMP;
            }
            // Some sensors do not provide all values.
            if (value != null) {
                writeMeasurementPS.setObject(idx++, value, sqlType);
            } else {
                writeMeasurementPS.setNull(idx++, sqlType);
            }
        }
        writeMeasurementPS.setObject(idx, OffsetDateTime.now());
        // Manage writes to database
        if (cfg.timescaleDB.batchSize > 1) {
            batchCounter++;
            writeMeasurementPS.addBatch();
            if (batchCounter >= cfg.timescaleDB.batchSize) {
                batchCounter = 0;
                writeMeasurementPS.executeBatch();
            }
        } else {
            writeMeasurementPS.executeUpdate();
        }
    }
    /**
     * Writes sensor info to database if it doesn't exist. Updates sensor name if different from
     * configured. The check is performed only once per macAddress during runtime.
     */
    private void writeSensorInfo(EnhancedRuuviMeasurement measurement) throws SQLException {
        if (configuredSensors.containsKey(measurement.getMac())) {
            return;
        }
        var macAddress = measurement.getMac();
        LOG.info("Write sensor info for: " + measurement.getMac());
        var configuredName = cfg.sensor.macAddressToName.get(macAddress);
        readSensorData(macAddress);
        // Sensor not found
        if (!configuredSensors.containsKey(macAddress)) {
            var sql =
                    "INSERT INTO "
                            + SENSOR
                            + "(name, create_time, battery_change_time, mac_address)"
                            + " VALUES("
                            + configuredName
                            + ","
                            + "NOW(), NOW(), '"
                            + macAddress
                            + "')";
            executeUpdate(sql);
            // read again.
            readSensorData(macAddress);
        }
        // Sensor name in database differs from configured
        else if (!Objects.equals(configuredName, configuredSensors.get(macAddress).name)) {
            var sql =
                    "UPDATE "
                            + SENSOR
                            + " SET name = '"
                            + configuredName
                            + "' WHERE mac_address = '"
                            + macAddress
                            + "'";
            executeUpdate(sql);
            configuredSensors.get(macAddress).name = configuredName;
        }
    }

    /**
     * Returns sensor name from the database. An existing sensor without name is identified as empty
     * string whereas non-existing sensor is identified by NULL return value.
     *
     * @param macAddress
     * @return sensor name (null, empty or real)
     * @throws SQLException
     */
    private void readSensorData(String macAddress) throws SQLException {
        String sql = "SELECT id, name FROM " + SENSOR + " WHERE mac_address = '" + macAddress + "'";

        try (var stmt = con.createStatement()) {
            var rs = stmt.executeQuery(sql);
            if (rs.next()) {
                configuredSensors.put(
                        macAddress, new ConfiguredSensor(rs.getInt(1), rs.getString(2)));
            }
        }
    }

    private void executeUpdate(String sql) throws SQLException {
        LOG.info(sql);
        try (var stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void initWritePS() throws SQLException {

        if (writeMeasurementPS == null) {
            var fieldStr = "device_id," + toSnakeCase(cfg.storage.fields) + ",time";
            var paramStr =
                    "?,?,"
                            + cfg.storage.fields.stream()
                                    .map((s) -> "?")
                                    .collect(Collectors.joining(","));

            var sql =
                    "INSERT INTO "
                            + MEASUREMENT
                            + " ("
                            + fieldStr
                            + ")"
                            + " VALUES ("
                            + paramStr
                            + ")";
            writeMeasurementPS = con.prepareStatement(sql);
        }
    }

    private void createHypertable() throws SQLException {
        var sql =
                "SELECT * FROM create_hypertable('"
                        + MEASUREMENT
                        + "','time', if_not_exists => TRUE)";
        LOG.info(sql);
        try (var stmt = con.createStatement()) {
            var rs = stmt.executeQuery(sql);
            if (rs.next()) {
                LOG.info("Created hypertable: " + rs.getString(1));
            } else {
                throw new SQLException("Unable to create hypertable");
            }
        }
    }

    private static class ConfiguredSensor {
        ConfiguredSensor(int id, String name) {
            this.id = id;
            this.name = name;
        }

        int id;
        String name;
    }

    private String toSnakeCase(List<String> src) {
        return src.stream().map(this::toSnakeCase).collect(Collectors.joining(","));
    }

    private String toSnakeCase(String str) {
        return str.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
