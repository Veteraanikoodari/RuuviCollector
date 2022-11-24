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

  public static Builder builder(final String mac) {
    return new Builder(mac);
  }

  public static class Builder {
    private final String mac;
    private LimitingStrategy limitingStrategy;

    public Builder(final String mac) {
      this.mac = mac;
    }

    public Builder add(final String key, final String value) {
      if ("limitingStrategy".equals(key)) {
        if ("onMovement".equals(value)) {
          this.limitingStrategy = new DefaultDiscardingWithMotionSensitivityStrategy();
        }
      }
      return this;
    }

    public TagProperties build() {
      return new TagProperties(mac, limitingStrategy);
    }
  }
}
