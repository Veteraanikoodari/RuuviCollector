package fi.tkgwf.ruuvi.db;

import static fi.tkgwf.ruuvi.db.TimescaleDBUtil.*;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;

import java.sql.*;
import java.time.OffsetDateTime;
import java.util.*;

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

    public static TimescaleDBConnection fromConfiguration() throws SQLException {
        var url = Configuration.get().timescaleDB.url;
        var user = Configuration.get().timescaleDB.user;
        var pwd = Configuration.get().timescaleDB.pwd;
        // Auto-fix for minor detail.
        if (!url.endsWith("/")) {
            url += "/";
        }
        LOG.info("Connecting to database..");
        var con = DriverManager.getConnection(url + cfg.timescaleDB.database, user, pwd);
        LOG.info("..connected.");
        return new TimescaleDBConnection(con);
    }

    public static TimescaleDBConnection from(Connection con) throws SQLException {
        return new TimescaleDBConnection(con);
    }

    private TimescaleDBConnection(Connection connection) throws SQLException {
        con = connection;
        LOG.info("Configure reflection access to " + EnhancedRuuviMeasurement.class.getSimpleName());
        EnhancedRuuviMeasurement.enableCallFieldGetterByMethodName();

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
        executeUpdate(getCreateMeasurementTableIdxStr());
        executeSelectUpdate(getCreateHyperTableStr());
        executeUpdate(getCreateContinuousAggregate("five_minutes", "5 minutes"));
        executeSelectUpdate(getCreateContinuousAggregatePolicy("five_minutes", "1 month",
            "1 hour", "1 hour"));
        LOG.info("database configured.");
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
            executeUpdate(getWriteSensorInfoStr(macAddress));
            // read again to update cache
            readSensorData(macAddress);
        }
        // Sensor name in database differs from configured, update
        else if (!Objects.equals(configuredName, configuredSensors.get(macAddress).name)) {
            executeUpdate(getUpdateSensorInfoStr(macAddress));
            configuredSensors.get(macAddress).name = configuredName;
        }
    }

    /**
     * Read sensor from the database. An existing sensor is cached.
     *
     * @param macAddress sensor to read
     */
    private void readSensorData(String macAddress) throws SQLException {
        String sql = "SELECT id, name FROM " + SENSOR + " WHERE mac_address = '" + macAddress + "'";

        try (var stmt = con.createStatement()) {
            var rs = stmt.executeQuery(sql);
            if (rs.next()) {
                configuredSensors.put(macAddress, new ConfiguredSensor(rs.getInt(1), rs.getString(2)));
            }
        }
    }

    private void executeUpdate(String sql) throws SQLException {
        LOG.info(sql);
        try (var stmt = con.createStatement()) {
            stmt.executeUpdate(sql);
        }
    }

    private void executeSelectUpdate(String sql) throws SQLException {
        LOG.info(sql);
        try (var stmt = con.createStatement()) {
            var rs = stmt.executeQuery(sql);
            if (rs.next()) {
                LOG.info("Result: " + rs.getString(1));
            } else {
                throw new SQLException("Initialization error with statement: " + sql);
            }
        }
    }

    private void initWritePS() throws SQLException {
        if (writeMeasurementPS == null) {
            writeMeasurementPS = con.prepareStatement(getWriteMeasurementStr());
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
