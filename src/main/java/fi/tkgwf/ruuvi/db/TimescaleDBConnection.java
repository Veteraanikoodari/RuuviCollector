package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;
import org.apache.log4j.Logger;

public class TimescaleDBConnection implements RuuviDBConnection {

    private static final String MEASUREMENT = "measurement";
    private static final String SENSOR = "sensor";
    private static final Logger LOG = Logger.getLogger(TimescaleDBConnection.class);
    private static Configuration cfg = Configuration.get();
    private final Set<String> verifiedSensors = new HashSet<>();
    private Connection con;

    public static void main(String[] args) throws Exception {
        new TimescaleDBConnection();
    }

    public TimescaleDBConnection() throws SQLException {
        this(cfg.timescaleDB.url, cfg.timescaleDB.user, cfg.timescaleDB.pwd);
    }

    public TimescaleDBConnection(String url, String user, String pwd) throws SQLException {
        LOG.info("Connecting to database..");
        url = "jdbc:postgresql://192.168.1.28";
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
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void close() {}

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
        sb.replace(sb.length() - 1, sb.length(), ")");
        return sb.toString();
    }

    /** Hacky trick to derive datatype from field name. */
    private void appendField(StringBuilder sb, String name) {
        String dataType = " REAL";
        if (name.toLowerCase().endsWith("number") || name.toLowerCase().endsWith("counter")) {
            dataType = " INTEGER";
        } else if (name.toLowerCase().endsWith("time")) {
            dataType = " TIMESTAMPTZ NOT NULL";
        }
        sb.append(name).append(dataType).append(",");
    }

    private void executeUpdate(String sql) throws SQLException {
        LOG.info(sql);
        var stmt = con.createStatement();
        stmt.executeUpdate(sql);
        stmt.close();
    }

    private void writeSensorInfo(EnhancedRuuviMeasurement measurement) throws SQLException {
        if (verifiedSensors.contains(measurement.getMac()) || isSensorConfigured(measurement)) {
            return;
        }
        var sql =
                "INSERT INTO "
                        + SENSOR
                        + "(name, createTime, batteryChangeTime, macAddress)"
                        + " VALUES("
                        + cfg.sensor.macAddressToName.get(measurement.getMac())
                        + ","
                        + "NOW(), NOW(), "
                        + measurement.getMac()
                        + ")";

        executeUpdate(sql);
        verifiedSensors.add(measurement.getMac());
    }

    private boolean isSensorConfigured(EnhancedRuuviMeasurement measurement) throws SQLException {
        String sql = "SELECT FROM " + SENSOR + " WHERE macAddress = '" + measurement.getMac() + "'";
        var stmt = con.createStatement();
        var rs = stmt.executeQuery(sql);
        var isConfigured = rs.first();
        rs.close();
        stmt.close();
        return isConfigured;
    }
}
