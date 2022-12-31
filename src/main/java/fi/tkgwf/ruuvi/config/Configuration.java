package fi.tkgwf.ruuvi.config;

import fi.tkgwf.ruuvi.utils.Utils;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
public class Configuration {

  private static Configuration self;

  public SensorDefaults sensorDefaults;
  public List<Sensor> sensors;
  public List<Location> locations;
  public Storage storage;
  public TimescaleDB timescaleDB;
  public Prometheus prometheus;
  private Set<String> configuredMacAddresses;

  public static Configuration get() {
    if (self == null) {
      try {
        self = Utils.readYamlConfig(Configuration.class);
        self.init();
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
    return self;
  }

  public static class SensorDefaults {
    public long measurementUpdateLimitMs;
    public double motionSensitivityStrategyThreshold;
    public int motionSensitivityStrategyNumberOfPreviousMeasurementsToKeep;
    public String scanCommand;
    public String dumpCommand;
  }

  @Data
  @AllArgsConstructor
  @NoArgsConstructor
  public static class Sensor {
    private int id;
    public Integer locationId;
    public String macAddress;
    public String description;
  }

  public static class Location {
    public int id;
    public String name;
    public String description;
  }

  public static class Storage {
    public String receiver;
    public String method;
    public List<String> fields;
  }

  public static class TimescaleDB {
    public String url;
    public String database;
    public String user;
    public String pwd;
    public String grafanaUser;
    public String grafanaPwd;
    public boolean createTables;
    public int batchSize;
    public String rtAggregateSuffix;
    public String rtAggregateInterval;
    public String rtAggregateStartOffset;
    public String rtAggregateEndOffset;
    public String rtAggregateUpdateInterval;
    public String measurementRetentionPolicy;
  }

  public static class Prometheus {
    public int httpPort;
  }

  public boolean isConfiguredMac(String macAddress) {
    return configuredMacAddresses.contains(macAddress);
  }

  public Optional<Location> getLocation(Integer id) {
    return id == null
        ? Optional.empty()
        : locations.stream().filter(location -> id == location.id).findAny();
  }

  private void init() {
    configuredMacAddresses =
        sensors.stream().map(sensor -> sensor.macAddress).collect(Collectors.toSet());
  }
}
