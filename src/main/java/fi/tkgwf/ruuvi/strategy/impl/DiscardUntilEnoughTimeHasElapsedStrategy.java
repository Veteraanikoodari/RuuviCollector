package fi.tkgwf.ruuvi.strategy.impl;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.strategy.LimitingStrategy;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The default limiting strategy: discard measurements that are coming in too fast. The time limit
 * is defined as {@link Configuration#sensor#measurementUpdateLimit}. The limit is applied separately to all
 * the different devices sending data, i.e. per MAC address.
 */
public class DiscardUntilEnoughTimeHasElapsedStrategy implements LimitingStrategy {
    /** Contains the MAC address as key, and the timestamp of last sent update as value */
    long lastUpdateTime = System.currentTimeMillis();

    private final long updateLimit = Configuration.get().sensor.measurementUpdateLimitMs;

    @Override
    public Optional<EnhancedRuuviMeasurement> apply(final EnhancedRuuviMeasurement measurement) {
        if (!shouldUpdate()) {
            return Optional.empty();
        }
        return Optional.of(measurement);
    }

    private boolean shouldUpdate() {
        final long currentTime = System.currentTimeMillis();
        if (lastUpdateTime + updateLimit < currentTime) {
            lastUpdateTime = currentTime;
            return true;
        }
        return false;
    }
}
