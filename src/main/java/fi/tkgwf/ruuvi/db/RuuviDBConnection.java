package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;

public interface RuuviDBConnection {

  org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RuuviDBConnection.class);

  static RuuviDBConnection createDBConnection() {
    var method = Configuration.get().storage.method;
    log.info("Creating database connection for storageMethod: " + method);
    try {
      switch (method) {
        case "timescaleDB":
          return TimescaleDBConnection.fromConfiguration().autoConfigure();
        case "prometheus":
          return new PrometheusExporter(Configuration.get().prometheus.httpPort);
        case "dummy":
          return new DummyDBConnection();
        default:
          throw new IllegalArgumentException("Invalid storage method: " + method);
      }
    } catch (Throwable t) {
      log.error("Unable to configure storage method", t);
      log.info("Switching to logging mode only");
      return new DummyDBConnection();
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
