package fi.tkgwf.ruuvi.db;

import static fi.tkgwf.ruuvi.db.TimescaleDBUtil.*;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.service.PersistenceServiceException;
import fi.tkgwf.ruuvi.utils.Utils;
import java.sql.*;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimescaleDBConnection implements RuuviDBConnection {

  private static final Configuration cfg = Configuration.get();
  private final Map<String, ConfiguredSensor> configuredSensors = new HashMap<>();
  private final Connection con;
  // cached for efficiency
  private PreparedStatement writeMeasurementPS;
  private int batchCounter;

  public static TimescaleDBConnection fromConfiguration() throws SQLException {
    var url = cfg.timescaleDB.url;
    var user = cfg.timescaleDB.user;
    var pwd = cfg.timescaleDB.pwd;
    // Auto-fix for minor detail.
    if (!url.endsWith("/")) {
      url += "/";
    }
    log.info("Connecting to database..");
    var con = DriverManager.getConnection(url + cfg.timescaleDB.database, user, pwd);
    log.info("..connected.");
    return new TimescaleDBConnection(con);
  }

  public static TimescaleDBConnection from(Connection con) throws SQLException {
    return new TimescaleDBConnection(con);
  }

  private TimescaleDBConnection(Connection connection) throws SQLException {
    con = connection;
    log.info("Configure reflection access to " + EnhancedRuuviMeasurement.class.getSimpleName());
    EnhancedRuuviMeasurement.enableCallFieldGetterByMethodName();
  }

  public TimescaleDBConnection autoConfigure() throws SQLException {
    if (cfg.timescaleDB.createTables) {
      createTables();
      var grafanaUser = cfg.timescaleDB.grafanaUser;
      var grafanaPwd = cfg.timescaleDB.grafanaPwd;
      if (grafanaUser != null && grafanaPwd != null) {
        createUser(grafanaUser, grafanaPwd);
      }
    }
    return this;
  }

  @Override
  public void save(EnhancedRuuviMeasurement measurement) {
    try {
      writeSensorInfo(measurement);
      writeMeasurement(measurement);
    } catch (SQLException e) {
      throw new PersistenceServiceException(e);
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

  private void createUser(String user, String pwd) {
    log.info("-- Creating user with view only privileges: " + user);
    var sql = String.format("SELECT 1 FROM pg_roles WHERE rolname='%s'", user);
    if (executeQueryReturnsNoRows(sql)) {
      getCreateUserStr(user, pwd).forEach(this::executeUpdate);
    }
    log.info("-- User created: " + user);
  }

  private void createTables() throws SQLException {
    // TODO continuos_aggregate to real time aggregate
    var db = cfg.timescaleDB.database;
    log.info("-- Creating tables for database: " + db);
    executeUpdate(getSensorTableStr());
    executeUpdate(getMeasurementTableStr());
    executeUpdate(getCreateMeasurementTableIdxStr());
    executeSelectUpdate(getCreateHyperTableStr());
    executeUpdate(getCreateRealtimeAggregate("five_minutes", "5 minutes"));
    executeSelectUpdate(
        getCreateContinuousAggregatePolicy("five_minutes", "1 month", "1 hour", "1 hour"));
    log.info("-- Database configured: " + db);
  }

  private void writeMeasurement(EnhancedRuuviMeasurement measurement) throws SQLException {
    initWritePS();
    int idx = 1;
    // Manage preparedStatement values
    writeMeasurementPS.setInt(idx++, configuredSensors.get(measurement.getMac()).id);
    for (var name : cfg.storage.fields) {
      var value = measurement.getFieldValue(name);
      log.debug("Field: " + name + " Value: " + value);
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
    writeMeasurementPS.setObject(idx, Utils.currentTime());
    // Manage writes to database
    if (cfg.timescaleDB.batchSize > 1) {
      batchCounter++;
      writeMeasurementPS.addBatch();
      if (batchCounter >= cfg.timescaleDB.batchSize) {
        batchCounter = 0;
        writeMeasurementPS.executeBatch();
      }
    } else {
      log.info("Write measurement: " + measurement.toString());
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
    var configuredName = cfg.sensor.macAddressToName.get(macAddress);
    log.info("Write sensor info for: {} {}", measurement.getMac(), configuredName);
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
    String sql = "SELECT id, name FROM " + SENSOR_TBL + " WHERE mac_address = '" + macAddress + "'";

    try (var stmt = con.createStatement()) {
      var rs = stmt.executeQuery(sql);
      if (rs.next()) {
        configuredSensors.put(macAddress, new ConfiguredSensor(rs.getInt(1), rs.getString(2)));
      }
    }
  }

  private void executeUpdate(String sql) {
    log.info(sql);
    try (var stmt = con.createStatement()) {
      stmt.executeUpdate(sql);
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void executeSelectUpdate(String sql) throws SQLException {
    log.info(sql);
    try (var stmt = con.createStatement()) {
      var rs = stmt.executeQuery(sql);
      if (rs.next()) {
        log.info("Result: " + rs.getString(1));
      } else {
        throw new SQLException("Initialization error with statement: " + sql);
      }
    }
  }

  private boolean executeQueryReturnsNoRows(String sql) {
    try (var stmt = con.createStatement()) {
      var rs = stmt.executeQuery(sql);
      return !rs.next();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  private void initWritePS() throws SQLException {
    if (writeMeasurementPS == null) {
      writeMeasurementPS = con.prepareStatement(getWriteMeasurementStr());
    }
  }

  @AllArgsConstructor
  private static class ConfiguredSensor {
    int id;
    String name;
  }
}
