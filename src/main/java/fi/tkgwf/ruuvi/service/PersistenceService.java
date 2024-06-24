package fi.tkgwf.ruuvi.service;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.TagProperties;
import fi.tkgwf.ruuvi.db.RuuviDBConnection;
import fi.tkgwf.ruuvi.strategy.LimitingStrategy;
import java.util.Optional;

public class PersistenceService implements AutoCloseable {
  private RuuviDBConnection db;

  public PersistenceService() {
    this(RuuviDBConnection.createDBConnection());
  }

  public PersistenceService(final RuuviDBConnection db) {
    this.db = db;
  }

  @Override
  public void close() {
    db.close();
  }

  public void store(final EnhancedRuuviMeasurement measurement) throws PersistenceServiceException {
    Optional.ofNullable(measurement.getMac())
        .map(mac -> TagProperties.get(mac).getLimitingStrategy())
        .orElse(LimitingStrategy.DEFAULT)
        .apply(measurement)
        .ifPresent(db::save);
  }
}
