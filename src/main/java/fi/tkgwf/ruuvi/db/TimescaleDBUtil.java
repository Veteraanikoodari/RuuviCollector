package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.config.Configuration;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Utility class to create SQL statements from configured Ruuvitag fields.
 */
public class TimescaleDBUtil {

    public static final String MEASUREMENT_TBL = "measurement";
    public static final String SENSOR_TBL = "sensor";

    public static String getSensorTableStr() {
        return "CREATE TABLE IF NOT EXISTS "
            + SENSOR_TBL
            + " ("
            + " id INTEGER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,"
            + " name TEXT UNIQUE,"
            + " description TEXT,"
            + " create_time TIMESTAMPTZ,"
            + " battery_change_time TIMESTAMPTZ,"
            + " mac_address TEXT UNIQUE)";
    }

    public static String getMeasurementTableStr() {
        StringBuilder sb = new StringBuilder("CREATE TABLE IF NOT EXISTS " + MEASUREMENT_TBL);
        sb.append(" (");
        sb.append("device_id INTEGER REFERENCES " + SENSOR_TBL + "(id) ON DELETE RESTRICT, ");
        Configuration.get().storage.fields.forEach(field -> appendField(sb, field));
        sb.append(" time TIMESTAMPTZ NOT NULL)");
        return sb.toString();
    }

    public static String getWriteMeasurementStr() {
        var storage = Configuration.get().storage;
        var fieldStr = "device_id," + toSnakeCase(storage.fields) + ",time";
        var paramStr =
            "?,?," + storage.fields.stream().map((s) -> "?").collect(Collectors.joining(","));

        return "INSERT INTO " + MEASUREMENT_TBL + " (" + fieldStr + ")" + " VALUES (" + paramStr + ")";
    }

    public static String getWriteSensorInfoStr(String macAddress) {
        var configuredName = Configuration.get().sensor.macAddressToName.get(macAddress);
        return "INSERT INTO "
            + SENSOR_TBL
            + "(name, create_time, battery_change_time, mac_address)"
            + " VALUES("
            + configuredName
            + ","
            + "NOW(), NOW(), '"
            + macAddress
            + "')";
    }

    public static String getUpdateSensorInfoStr(String macAddress) {
        var configuredName = Configuration.get().sensor.macAddressToName.get(macAddress);
        return "UPDATE "
            + SENSOR_TBL
            + " SET name = '"
            + configuredName
            + "' WHERE mac_address = '"
            + macAddress
            + "'";
    }

    /**
     * Create continuous aggregate with time_bucket function.
     *
     * @param suffix   view name measurement_suffix
     * @param interval postgresql interval. e.g. 1 month
     */
    public static String getCreateContinuousAggregate(String suffix, String interval) {
        var fields = Configuration.get().storage.fields;
        var averages =
            fields.stream()
                .map(name -> "AVG(" + toSnakeCase(name) + ") AS " + toSnakeCase(name) + "_avg")
                .collect(Collectors.joining(", "));

        return "CREATE MATERIALIZED VIEW IF NOT EXISTS "
            + MEASUREMENT_TBL
            + "_"
            + suffix
            + " WITH (timescaledb.continuous, timescaledb.materialized_only = true) AS SELECT"
            + " time_bucket(interval '" + interval + "', \"time\") AS bucket, "
            + averages
            + ", device_id FROM "
            + MEASUREMENT_TBL
            + " GROUP BY bucket, device_id";
    }

    public static String getCreateContinuousAggregatePolicy(String suffix, String startOffset,
                                                            String endOffset, String interval) {
        return String.format(
            "SELECT add_continuous_aggregate_policy('" + MEASUREMENT_TBL + "_" + suffix + "',"
            + " start_offset => INTERVAL '%s',"
            + " end_offset => INTERVAL '%s',"
            + "schedule_interval => INTERVAL '%s');", startOffset, endOffset, interval);
    }

    public static String getCreateMeasurementTableIdxStr() {
        return "CREATE INDEX IF NOT EXISTS idx_device_id_time ON "
            + MEASUREMENT_TBL
            + " (device_id, time DESC)";
    }

    public static String getCreateHyperTableStr() {
        return "SELECT * FROM create_hypertable('"
            + MEASUREMENT_TBL
            + "','time', if_not_exists => TRUE)";
    }

    public static boolean isIntField(String name) {
        return name.endsWith("Number") || name.endsWith("Counter");
    }

    public static boolean isTimeField(String name) {
        return name.endsWith("Time");
    }

    /**
     * Hacky trick to derive datatype from field name.
     */
    private static void appendField(StringBuilder sb, String name) {
        String dataType = " DOUBLE PRECISION";
        if (isIntField(name)) {
            dataType = " INTEGER";
        } else if (isTimeField(name)) {
            dataType = " TIMESTAMPTZ NOT NULL";
        }
        sb.append(toSnakeCase(name)).append(dataType).append(",");
    }

    public static String toSnakeCase(List<String> src) {
        return src.stream().map(TimescaleDBUtil::toSnakeCase).collect(Collectors.joining(","));
    }

    public static String toSnakeCase(String str) {
        return str.replaceAll("([A-Z])", "_$1").toLowerCase();
    }
}
