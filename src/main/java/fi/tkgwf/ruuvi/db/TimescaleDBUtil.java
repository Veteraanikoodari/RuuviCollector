package fi.tkgwf.ruuvi.db;

import static fi.tkgwf.ruuvi.utils.Utils.toSnakeCase;

import fi.tkgwf.ruuvi.config.Configuration;
import java.util.List;
import java.util.stream.Collectors;

/** Utility class to create SQL statements from configured Ruuvitag fields. */
public class TimescaleDBUtil {

  static final String MEASUREMENT_TBL = "measurement";
  static final String SENSOR_TBL = "sensor";
  static final String LOCATION_TBL = "location";
  static final String SENSOR_LOCATION_TBL = SENSOR_TBL + "_" + LOCATION_TBL;

  private static final String VALID_RUUVI_MAC_ADDRESS = "^[A-F0-9]+$";

  public static List<String> getCreateUserStr(String user, String pwd) {
    String db = Configuration.get().timescaleDB.database;
    return List.of(
        String.format("CREATE USER %s WITH PASSWORD '%s';", user, pwd),
        String.format("GRANT CONNECT ON DATABASE %s TO %s;", db, user),
        String.format("GRANT USAGE ON SCHEMA public TO %s;", user),
        String.format("GRANT SELECT ON ALL TABLES IN SCHEMA public TO %s;", user),
        // For future tables also
        String.format(
            "ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO %s;", user));
  }

  public static String getSensorTableStr() {
    var raw =
        "CREATE TABLE IF NOT EXISTS %s ("
            + " id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,"
            + " description TEXT,"
            + " start_time TIMESTAMPTZ,"
            + " end_time TIMESTAMPTZ,"
            + " battery_change_time TIMESTAMPTZ,"
            + " mac_address TEXT UNIQUE)";
    return String.format(raw, SENSOR_TBL);
  }

  public static String getLocationTableStr() {
    var raw =
        "CREATE TABLE IF NOT EXISTS %s ("
            + " id INTEGER PRIMARY KEY,"
            + " name TEXT UNIQUE,"
            + " description TEXT)";
    return String.format(raw, LOCATION_TBL);
  }

  /**
   * NOTE: here the device_id and location_id are defined as unique. There are cases where different
   * approach could be warranted. One such example is to increase accuracy by designating multiple
   * sensors to a single location. That would effectively lead to averaging values from all sensors
   * when reading data within the timescale timebucket.
   */
  public static String getSensorLocationTableStr() {
    var raw =
        "CREATE TABLE IF NOT EXISTS %s ("
            + " device_id INTEGER REFERENCES %s (id) ON DELETE RESTRICT,"
            + " location_id INTEGER REFERENCES %s (id) ON DELETE RESTRICT,"
            + " start_time TIMESTAMPTZ,"
            + " end_time TIMESTAMPTZ,"
            + " UNIQUE(device_id, location_id))";
    return String.format(raw, SENSOR_LOCATION_TBL, SENSOR_TBL, LOCATION_TBL);
  }

  public static String getMeasurementTableStr() {
    StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + MEASUREMENT_TBL);
    sb.append(" (");
    sb.append("device_id INTEGER REFERENCES " + SENSOR_TBL + "(id) ON DELETE RESTRICT, ");
    sb.append("location_id INTEGER REFERENCES " + LOCATION_TBL + "(id) ON DELETE RESTRICT, ");
    Configuration.get().storage.fields.forEach(field -> appendField(sb, field));
    sb.append(" time TIMESTAMPTZ NOT NULL)");
    return sb.toString();
  }

  public static String getWriteMeasurementStr() {
    var storage = Configuration.get().storage;
    var fieldStr = "device_id, location_id," + toSnakeCase(storage.fields) + ",time";
    var paramStr =
        "?,?,?," + storage.fields.stream().map((s) -> "?").collect(Collectors.joining(","));

    return String.format("INSERT INTO %s (%s) VALUES (%s)", MEASUREMENT_TBL, fieldStr, paramStr);
  }

  public static String getInsertSensorInfoStr(Configuration.Sensor sensor) {
    var raw =
        "INSERT INTO %s (description, start_time, battery_change_time, mac_address)"
            + " VALUES('%s', NOW(), NOW(), '%s')";
    return String.format(raw, SENSOR_TBL, sensor.description, sensor.macAddress);
  }

  public static String getWriteLocationInfoStr(int id, String name, String description) {
    var raw =
        "INSERT INTO %s (id, name, description)"
            + " VALUES(%d, '%s', '%s') ON CONFLICT (name) DO NOTHING";
    return String.format(raw, LOCATION_TBL, id, name, description);
  }

  public static String getUpsertSensorLocationInfoStr(Configuration.Sensor sensor) {
    int devId = sensor.getId();
    int locId = sensor.getLocationId();

    var raw =
        "INSERT INTO %s (device_id, location_id, start_time)"
            + " VALUES('%d', '%d', NOW()) ON CONFLICT (device_id, location_id) DO UPDATE"
            + " SET location_id = %d WHERE %s.device_id = %d";
    return String.format(raw, SENSOR_LOCATION_TBL, devId, locId, locId, SENSOR_LOCATION_TBL, devId);
  }

  public static String getUpdateSensorInfoStr(Configuration.Sensor configuredSensor) {
    var raw = "UPDATE %s SET description = '%s' WHERE mac_address = '%s'";
    return String.format(
        raw, SENSOR_TBL, configuredSensor.description, configuredSensor.macAddress);
  }

  /**
   * Create real-time aggregate with time_bucket function. NOTE the suffix is also used as a 'bucket
   * name' for the averages.
   *
   * @param suffix view name measurement_suffix
   * @param interval postgresql interval. e.g. 1 month
   */
  public static String getCreateAggregate(String suffix, String interval) {
    var fields = Configuration.get().storage.fieldsAggregated;
    var averages =
        fields.stream()
            .map(name -> "AVG(" + toSnakeCase(name) + ") AS " + toSnakeCase(name) + "_avg")
            .collect(Collectors.joining(", "));

    var raw =
        "CREATE MATERIALIZED VIEW IF NOT EXISTS %s_%s"
            + " WITH (timescaledb.continuous, timescaledb.materialized_only = false)"
            + " AS SELECT time_bucket(interval '%s', \"time\") AS %s, %s, device_id"
            + " FROM %s GROUP BY %s, device_id WITH NO DATA";

    return String.format(
        raw, MEASUREMENT_TBL, suffix, interval, suffix, averages, MEASUREMENT_TBL, suffix);
  }

  public static String getCreateAggregatePolicy(
      String suffix, String startOffset, String endOffset, String interval) {

    return String.format(
        "SELECT add_continuous_aggregate_policy('%s_%s',"
            + " start_offset => INTERVAL '%s',"
            + " end_offset => INTERVAL '%s',"
            + "schedule_interval => INTERVAL '%s', if_not_exists => TRUE);",
        MEASUREMENT_TBL, suffix, startOffset, endOffset, interval);
  }

  public static String getRefreshAllInAggregateQuery(String table) {
    return String.format("CALL refresh_continuous_aggregate('%s', NULL, NULL)", table);
  }

  public static String getCreateRetentionPolicy(String table, String interval) {
    return String.format("SELECT add_retention_policy('%s', INTERVAL '%s')", table, interval);
  }

  public static String getCreateMeasurementTableIdxStr() {
    return String.format(
        "CREATE INDEX IF NOT EXISTS idx_device_id_time ON %s" + " (device_id, time DESC)",
        MEASUREMENT_TBL);
  }

  public static String getCreateHyperTableStr() {
    return String.format(
        "SELECT * FROM create_hypertable('%s','time', if_not_exists => TRUE)", MEASUREMENT_TBL);
  }

  public static boolean isIntField(String name) {
    return name.endsWith("Number") || name.endsWith("Counter");
  }

  public static boolean isTimeField(String name) {
    return name.endsWith("Time");
  }

  public static boolean isValidMacAddress(String mac) {
    return mac != null && mac.length() == 12 && mac.matches(VALID_RUUVI_MAC_ADDRESS);
  }

  /** Hacky trick to derive datatype from field name. */
  private static void appendField(StringBuilder sb, String name) {
    String dataType = " DOUBLE PRECISION";
    if (isIntField(name)) {
      dataType = " INTEGER";
    } else if (isTimeField(name)) {
      dataType = " TIMESTAMPTZ NOT NULL";
    }
    sb.append(toSnakeCase(name)).append(dataType).append(",");
  }
}
