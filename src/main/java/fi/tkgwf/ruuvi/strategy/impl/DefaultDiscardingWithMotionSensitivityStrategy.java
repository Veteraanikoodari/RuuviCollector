package fi.tkgwf.ruuvi.strategy.impl;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.strategy.LimitingStrategy;
import java.util.*;

/**
 * Normally discard measurements that are coming in too fast, except when a sudden acceleration
 * change takes place -- in that case the measurement is always saved.
 *
 * <p>The time limit is defined as {@link Configuration#sensorDefaults#measurementUpdateLimit}. The
 * acceleration bounds are defined as values compared to the previous measurement with {@link
 * Configuration#sensorDefaults#defaultWithMotionSensitivityStrategyThreshold}.
 *
 * <p>The limit is applied separately to all the different devices sending data, i.e. per MAC
 * address.
 */
public class DefaultDiscardingWithMotionSensitivityStrategy implements LimitingStrategy {
  private final DiscardUntilEnoughTimeHasElapsedStrategy defaultStrategy =
      new DiscardUntilEnoughTimeHasElapsedStrategy();

  private final Double threshold =
      Configuration.get().sensorDefaults.motionSensitivityStrategyThreshold;
  private final List<EnhancedRuuviMeasurement> previousMeasurements = new ArrayList<>();
  private boolean previousOutsideOfRange = false;

  @Override
  public Optional<EnhancedRuuviMeasurement> apply(final EnhancedRuuviMeasurement measurement) {
    previousMeasurements.add(measurement);
    if (previousMeasurements.size()
        > Configuration.get()
            .sensorDefaults
            .motionSensitivityStrategyNumberOfPreviousMeasurementsToKeep) {
      previousMeasurements.remove(0);
    }
    // Always apply the default strategy to keep the timestamps updated there:
    Optional<EnhancedRuuviMeasurement> result = defaultStrategy.apply(measurement);

    // Apply the motion sensing strategy only if the base strategy says "no":
    if (result.isEmpty() && previousMeasurements.size() > 1) {
      final EnhancedRuuviMeasurement previous =
          previousMeasurements.get(previousMeasurements.size() - 2);
      if (isOutsideThreshold(measurement.getAccelerationX(), previous.getAccelerationX())
          || isOutsideThreshold(measurement.getAccelerationY(), previous.getAccelerationY())
          || isOutsideThreshold(measurement.getAccelerationZ(), previous.getAccelerationZ())) {
        result = Optional.of(measurement);
        previousOutsideOfRange = true;
      } else if (previousOutsideOfRange) {
        // Reset the measurements: store one more event after the values have returned to
        // within the threshold
        result = Optional.of(measurement);
        previousOutsideOfRange = false;
      }
    }

    return result;
  }

  private boolean isOutsideThreshold(final Double current, final Double previous) {
    if (current == null || previous == null) {
      return false;
    }
    final double upperBound = previous + threshold;
    final double lowerBound = previous - threshold;
    return current > upperBound || current < lowerBound;
  }
}
