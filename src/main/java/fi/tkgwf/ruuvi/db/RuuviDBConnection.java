package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import org.apache.log4j.Logger;

public interface RuuviDBConnection {

    Logger LOG = Logger.getLogger(RuuviDBConnection.class);

    static RuuviDBConnection createDBConnection() {
        var method = Configuration.get().storage.method;
        LOG.info("Creating database connection for storageMethod: " + method);
        switch (method) {
            case "influxdb":
                return new InfluxDBConnection();
            case "influxdb2":
                return new InfluxDB2Connection();
            case "prometheus":
                return new PrometheusExporter(Configuration.get().prometheus.httpPort);
            case "dummy":
                return new DummyDBConnection();
            default:
                throw new IllegalArgumentException("Invalid storage method: " + method);
        }
    }

    /**
     * Saves the measurement
     *
     * @param measurement
     */
    void save(EnhancedRuuviMeasurement measurement);

    /** Closes the DB connection */
    void close();
}
