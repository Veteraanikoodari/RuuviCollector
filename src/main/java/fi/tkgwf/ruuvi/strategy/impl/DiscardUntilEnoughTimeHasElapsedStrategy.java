package fi.tkgwf.ruuvi.strategy.impl;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.strategy.LimitingStrategy;
import fi.tkgwf.ruuvi.utils.Utils;
import java.util.Optional;

/**
 * The default limiting strategy: discard measurements that are coming in too fast. The time limit
 * is defined as {@link Configuration#sensorDefaults#measurementUpdateLimit}. The limit is applied
 * separately to all the different devices sending data, i.e. per MAC address.
 */
public class DiscardUntilEnoughTimeHasElapsedStrategy implements LimitingStrategy {
  private final long updateLimit = Configuration.get().sensorDefaults.measurementUpdateLimitMs;
  // set initial update time so that the first incoming measurement is accepted.
  private long lastUpdateTime = Utils.currentTimeMillis() - updateLimit;

  @Override
  public Optional<EnhancedRuuviMeasurement> apply(final EnhancedRuuviMeasurement measurement) {
    if (!shouldUpdate()) {
      return Optional.empty();
    }
    return Optional.of(measurement);
  }

  private boolean shouldUpdate() {
    final long currentTime = Utils.currentTimeMillis();
    if (lastUpdateTime + updateLimit <= currentTime) {
      lastUpdateTime = currentTime;
      return true;
    }
    return false;
  }
}
