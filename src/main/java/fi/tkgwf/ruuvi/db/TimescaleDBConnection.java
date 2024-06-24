package fi.tkgwf.ruuvi.db;

import static fi.tkgwf.ruuvi.db.TimescaleDBUtil.*;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.service.PersistenceServiceException;
import fi.tkgwf.ruuvi.utils.Utils;
import java.sql.*;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TimescaleDBConnection implements RuuviDBConnection {

  private static final Configuration cfg = Configuration.get();
  private final Connection con;
  // cached for efficiency
  private PreparedStatement writeMeasurementPS;
  private int batchCounter = 1;
  // track number of measurements written to database.
  private int totalMeasurementWrites;
  private Set<String> unidentifiedSensors = new HashSet<>();
  private Map<String, Configuration.Sensor> macAddressToSensor;

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
    macAddressToSensor =
        cfg.sensors.stream()
            .collect(Collectors.toMap(sensor -> sensor.macAddress, Function.identity()));
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
    writeLocationInfo();
    writeSensorInfo();
    updateSensorLocations();

    return this;
  }

  public int getTotalMeasurementWrites() {
    return totalMeasurementWrites;
  }

  @Override
  public void save(EnhancedRuuviMeasurement measurement) {
    try {
      if (macAddressToSensor.containsKey(measurement.getMac())) {
        writeMeasurement(measurement);
      } else {
        maybeLogUnidentifiedSensor(measurement.getMac());
      }
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

  private void createLocations() {}

  private void createTables() throws SQLException {
    final var db = cfg.timescaleDB.database;
    final var cnf = cfg.timescaleDB;
    log.info("-- Creating tables for database: " + db);
    executeUpdate(getSensorTableStr());
    executeUpdate(getLocationTableStr());
    executeUpdate(getSensorLocationTableStr());
    executeUpdate(getMeasurementTableStr());
    executeUpdate(getCreateMeasurementTableIdxStr());
    executeSelectUpdate(getCreateHyperTableStr());
    // fixed RTA with five_minute buckets
    executeUpdate(getCreateAggregate(cnf.rtAggregateSuffix, cnf.rtAggregateInterval));
    // update policy for RTA
    var policyCreated =
        executeSelectUpdate(
            getCreateAggregatePolicy(
                cnf.rtAggregateSuffix,
                cnf.rtAggregateStartOffset,
                cnf.rtAggregateEndOffset,
                cnf.rtAggregateUpdateInterval));
    if (policyCreated) {
      // NOTE: This must not be called on subsequent runs!
      // It would destroy aggregate data older that following retention window
      // executeUpdate(getRefreshAllInAggregateQuery(MEASUREMENT_TBL + "_five_minutes"));
      // retention policy for raw data.
      executeSelectUpdate(
          getCreateRetentionPolicy(MEASUREMENT_TBL, cnf.measurementRetentionPolicy));
    }
    log.info("-- Database configured: " + db);
  }

  private void writeMeasurement(EnhancedRuuviMeasurement measurement) throws SQLException {
    initWritePS();
    int idx = 1;
    // Manage preparedStatement values
    var sensor = macAddressToSensor.get(measurement.getMac());
    writeMeasurementPS.setInt(idx++, sensor.getId());
    writeMeasurementPS.setInt(idx++, sensor.getLocationId());
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
    writeMeasurementPS.addBatch();
    if (++batchCounter > cfg.timescaleDB.batchSize) {
      batchCounter = 1;
      writeMeasurementPS.executeBatch();
      totalMeasurementWrites += cfg.timescaleDB.batchSize;
    }
  }

  private void writeLocationInfo() {
    cfg.locations.forEach(
        location ->
            executeUpdate(
                getWriteLocationInfoStr(location.id, location.name, location.description)));
  }

  /**
   * Read / Write / Update sensor info from/to database. Remove sensors with invalid configurations
   * from map.
   */
  private void writeSensorInfo() {
    cfg.sensors.forEach(
        configured -> {
          getValidSensorConfiguration(configured.macAddress)
              .ifPresentOrElse(
                  valid -> {
                    readSensorInfo(valid)
                        .ifPresentOrElse(
                            fromDatabase -> {
                              if (needsUpdate(configured, fromDatabase)) {
                                executeUpdate(getUpdateSensorInfoStr(configured));
                              }
                            },
                            () -> executeUpdate(getInsertSensorInfoStr(configured)));
                    // read created or updated info to map.
                    readSensorInfo(configured)
                        .ifPresent(sensor -> macAddressToSensor.put(sensor.macAddress, sensor));
                  },
                  () -> macAddressToSensor.remove(configured.macAddress));
        });
  }

  private void updateSensorLocations() {
    cfg.sensors.forEach(
        sensor ->
            getValidSensorConfiguration(sensor.macAddress)
                .ifPresent(valid -> executeUpdate(getUpsertSensorLocationInfoStr(valid))));
  }

  /** Read sensor from the database. An existing sensor is cached. */
  private Optional<Configuration.Sensor> readSensorInfo(Configuration.Sensor configuredSensor)
      throws PersistenceServiceException {
    String sql =
        String.format(
            "SELECT id, description FROM " + SENSOR_TBL + " WHERE mac_address = '%s'",
            configuredSensor.macAddress);

    Configuration.Sensor sensorFromDB = null;

    try (var stmt = con.createStatement()) {
      var rs = stmt.executeQuery(sql);
      if (rs.next()) {
        sensorFromDB =
            new Configuration.Sensor(
                rs.getInt(1),
                configuredSensor.locationId,
                configuredSensor.macAddress,
                rs.getString(2));
      }
    } catch (SQLException e) {
      throw new PersistenceServiceException(e);
    }
    return Optional.ofNullable(sensorFromDB);
  }

  private Optional<Configuration.Sensor> getValidSensorConfiguration(String macAddress) {
    var configuredSensor = macAddressToSensor.get(macAddress);

    if (configuredSensor == null) {
      log.info("Detected sensor {} is not configured. Skipping..", macAddress);
    } else if (cfg.getLocation(configuredSensor.getLocationId()).isEmpty()) {
      log.info("Detected sensor {} has invalid or missing location id. Skipping..", macAddress);
      configuredSensor = null;
    } else if (!isValidMacAddress(configuredSensor.getMacAddress())) {
      log.info("Sensor mac address is invalid: {}", configuredSensor.getMacAddress());
      configuredSensor = null;
    }
    return Optional.ofNullable(configuredSensor);
  }

  private void executeUpdate(String sql) {
    log.info(sql);
    try (var stmt = con.createStatement()) {
      stmt.executeUpdate(sql);
    } catch (SQLException e) {
      throw new PersistenceServiceException(e);
    }
  }

  /** Run conditional update (IF NOT EXISTS). Return true if operation was actually run. */
  private boolean executeSelectUpdate(String sql) throws SQLException {
    log.info(sql);
    boolean didUpdate = false;
    try (var stmt = con.createStatement()) {
      var rs = stmt.executeQuery(sql);
      if (rs.next()) {
        didUpdate = rs.getInt(1) != -1;
      } else {
        throw new SQLException("Initialization error with statement: " + sql);
      }
    }
    if (didUpdate) {
      log.info("update performed successfully.");
    } else {
      log.info("update not performed as object already exists.");
    }
    return didUpdate;
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

  private boolean needsUpdate(Configuration.Sensor configured, Configuration.Sensor existing) {
    return !Objects.equals(configured.description, existing.description);
  }

  private void maybeLogUnidentifiedSensor(String macAddress) {
    if (unidentifiedSensors.add(macAddress)) {
      log.info("Detected sensor with address {} that is not configured. Ignoring..", macAddress);
    }
  }
}
