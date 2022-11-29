package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DummyDBConnection implements RuuviDBConnection {

  @Override
  public void save(EnhancedRuuviMeasurement measurement) {
    log.debug(measurement.toString());
  }

  @Override
  public void close() {}
}
