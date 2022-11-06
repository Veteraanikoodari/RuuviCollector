package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
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

        LOG.info("Connecting to database..");
        con = DriverManager.getConnection(url + "/" + cfg.timescaleDB.database, user, pwd);
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
        executeUpdate(
                "SELECT create_hypertable('" + MEASUREMENT + "','time', if_not_exists => TRUE)");
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
                + " createTime TIMESTAMPTZ,"
                + " batteryChangeTime TIMESTAMPTZ,"
                + " macAddress MACADDR UNIQUE)";
    }

    private String getMeasurementTableStr() {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + MEASUREMENT);
        sb.append(" (");
        sb.append("deviceId INTEGER REFERENCES " + SENSOR + "(id) ON DELETE RESTRICT, ");
        Configuration.get().storage.fields.forEach(field -> appendField(sb, field));
        sb.append(" time TIMESTAMPTZ NOT NULL)");
        return sb.toString();
    }

    /** Hacky trick to derive datatype from field name. */
    private void appendField(StringBuilder sb, String name) {
        String dataType = " DOUBLE";
        if (isIntField(name)) {
            dataType = " INTEGER";
        } else if (isTimeField(name)) {
            dataType = " TIMESTAMPTZ NOT NULL";
        }
        sb.append(name).append(dataType).append(",");
    }

    private boolean isIntField(String name) {
        return name.endsWith("Number") || name.endsWith("Counter");
    }

    private boolean isTimeField(String name) {
        return name.endsWith("Time");
    }

    private void writeMeasurement(EnhancedRuuviMeasurement measurement) throws SQLException {
        initWritePS();
        int idx = 0;
        // Manage preparedStatement values
        for (var name : cfg.storage.fields) {
            var value = measurement.getFieldValue(name);
            if (isIntField(name)) {
                writeMeasurementPS.setInt(idx++, (Integer) value);
            } else {
                writeMeasurementPS.setDouble(idx++, (Double) value);
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
                            + "(name, createTime, batteryChangeTime, macAddress)"
                            + " VALUES("
                            + configuredName
                            + ","
                            + "NOW(), NOW(), "
                            + macAddress
                            + ")";
            executeUpdate(sql);
        }
        // Sensor name in database differs from configured
        else if (!Objects.equals(configuredName, configuredSensors.get(macAddress).name)) {
            var sql =
                    "UPDATE "
                            + SENSOR
                            + " SET name = "
                            + configuredName
                            + " WHERE macAddress = "
                            + macAddress;
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
        String sql =
                "SELECT deviceId, name FROM " + SENSOR + " WHERE macAddress = '" + macAddress + "'";

        try (var stmt = con.createStatement()) {
            var rs = stmt.executeQuery(sql);
            if (rs.first()) {
                configuredSensors.put(
                        macAddress, new ConfiguredSensor(rs.getInt(0), rs.getString(1)));
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
            var fieldStr = "deviceId," + String.join(",", cfg.storage.fields) + ",time";
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

    private static class ConfiguredSensor {
        ConfiguredSensor(int id, String name) {
            this.id = id;
            this.name = name;
        }

        int id;
        String name;
    }
}
