package fi.tkgwf.ruuvi.config;

import fi.tkgwf.ruuvi.utils.Utils;
import java.io.IOException;
import java.util.List;
import java.util.Map;

public class Configuration {

    private static Configuration self;

    public Sensor sensor;
    public Storage storage;
    public TimescaleDB timescaleDB;
    public InfluxDB influxDB;
    public InfluxDB2 influxDB2;
    public InfluxCommon influxCommon;
    public Prometheus prometheus;

    public static Configuration get() {
        if (self == null) {
            try {
                self = Utils.readYamlConfig(Configuration.class);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        return self;
    }

    public static class Sensor {
        public long measurementUpdateLimitMs;
        public double motionSensitivityStrategyThreshold;
        public int motionSensitivityStrategyNumberOfPreviousMeasurementsToKeep;
        public Map<String, String> macAddressToName;
    }

    public static class Storage {
        public String method;
        public List<String> fields;
    }

    public static class TimescaleDB {
        public String url;
        public String database;
        public String user;
        public String pwd;
        public boolean createTables;
    }

    public static class InfluxDB {
        public String database;
        public String user;
        public String pwd;
    }

    public static class InfluxDB2 {
        public String org;
        public String token;
        public String bucket;
    }

    public static class InfluxCommon {
        public String url;
        public String measurement;
        public String retentionPolicy;
        public boolean gzip;
        public boolean batch;
        public boolean exitOnInfluxDBIOException;
        public int batchMaxSize;
        public long batchMaxTimeMs;
    }

    public static class Prometheus {
        public int httpPort;
    }
}
