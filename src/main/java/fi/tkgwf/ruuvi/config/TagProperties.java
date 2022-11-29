package fi.tkgwf.ruuvi.config;

import fi.tkgwf.ruuvi.strategy.LimitingStrategy;
import fi.tkgwf.ruuvi.strategy.impl.DefaultDiscardingWithMotionSensitivityStrategy;
import java.util.*;

public class TagProperties {

  private static final Map<String, TagProperties> tagsInRange = new HashMap<>();

  /** Get tagProperties for macAddress. If address is new, new tagProperties object is created. */
  public static TagProperties get(String macAddress) {
    return tagsInRange.computeIfAbsent(
        macAddress,
        (absent) ->
            new TagProperties(macAddress, new DefaultDiscardingWithMotionSensitivityStrategy()));
  }

  private final String mac;
  private final LimitingStrategy limitingStrategy;

  private TagProperties(final String mac, final LimitingStrategy limitingStrategy) {
    this.mac = mac;
    this.limitingStrategy = limitingStrategy;
  }

  public String getMac() {
    return mac;
  }

  public LimitingStrategy getLimitingStrategy() {
    return limitingStrategy;
  }
}
