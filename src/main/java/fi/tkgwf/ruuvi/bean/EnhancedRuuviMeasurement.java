package fi.tkgwf.ruuvi.bean;

import fi.tkgwf.ruuvi.common.bean.RuuviMeasurement;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

/**
 * This class contains all the possible fields/data acquirable from a RuuviTag in a "human format",
 * for example the temperature as a decimal number rather than an integer meaning one 200th of a
 * degree. Not all fields are necessarily present depending on the data format and implementations.
 */
public class EnhancedRuuviMeasurement extends RuuviMeasurement {

  public EnhancedRuuviMeasurement() {
    super();
  }

  public EnhancedRuuviMeasurement(RuuviMeasurement m) {
    this();
    this.setDataFormat(m.getDataFormat());
    this.setTemperature(m.getTemperature());
    this.setHumidity(m.getHumidity());
    this.setPressure(m.getPressure());
    this.setAccelerationX(m.getAccelerationX());
    this.setAccelerationY(m.getAccelerationY());
    this.setAccelerationZ(m.getAccelerationZ());
    this.setBatteryVoltage(m.getBatteryVoltage());
    this.setTxPower(m.getTxPower());
    this.setMovementCounter(m.getMovementCounter());
    this.setMeasurementSequenceNumber(m.getMeasurementSequenceNumber());
  }

  private static Map<String, Method> nameToGetter;

  /** Timestamp in milliseconds, normally not populated to use local time */
  private Long time;
  /** Friendly name for the tag */
  private String name;
  /** MAC address of the tag as seen by the receiver */
  private String mac;
  /** Arbitrary string associated with the receiver. */
  private String receiver;
  /** The RSSI at the receiver */
  private Integer rssi;
  /** Total acceleration */
  private Double accelerationTotal;
  /** The angle between the acceleration vector and X axis */
  private Double accelerationAngleFromX;
  /** The angle between the acceleration vector and Y axis */
  private Double accelerationAngleFromY;
  /** The angle between the acceleration vector and Z axis */
  private Double accelerationAngleFromZ;
  /** Absolute humidity in g/m^3 */
  private Double absoluteHumidity;
  /** Dew point in Celsius */
  private Double dewPoint;
  /** Vapor pressure of water */
  private Double equilibriumVaporPressure;
  /** Density of air */
  private Double airDensity;

  public Long getTime() {
    return time;
  }

  public void setTime(Long time) {
    this.time = time;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getMac() {
    return mac;
  }

  public void setMac(String mac) {
    this.mac = mac;
  }

  public String getReceiver() {
    return receiver;
  }

  public void setReceiver(String receiver) {
    this.receiver = receiver;
  }

  public Integer getRssi() {
    return rssi;
  }

  public void setRssi(Integer rssi) {
    this.rssi = rssi;
  }

  public Double getAccelerationTotal() {
    return accelerationTotal;
  }

  public void setAccelerationTotal(Double accelerationTotal) {
    this.accelerationTotal = accelerationTotal;
  }

  public Double getAccelerationAngleFromX() {
    return accelerationAngleFromX;
  }

  public void setAccelerationAngleFromX(Double accelerationAngleFromX) {
    this.accelerationAngleFromX = accelerationAngleFromX;
  }

  public Double getAccelerationAngleFromY() {
    return accelerationAngleFromY;
  }

  public void setAccelerationAngleFromY(Double accelerationAngleFromY) {
    this.accelerationAngleFromY = accelerationAngleFromY;
  }

  public Double getAccelerationAngleFromZ() {
    return accelerationAngleFromZ;
  }

  public void setAccelerationAngleFromZ(Double accelerationAngleFromZ) {
    this.accelerationAngleFromZ = accelerationAngleFromZ;
  }

  public Double getAbsoluteHumidity() {
    return absoluteHumidity;
  }

  public void setAbsoluteHumidity(Double absoluteHumidity) {
    this.absoluteHumidity = absoluteHumidity;
  }

  public Double getDewPoint() {
    return dewPoint;
  }

  public void setDewPoint(Double dewPoint) {
    this.dewPoint = dewPoint;
  }

  public Double getEquilibriumVaporPressure() {
    return equilibriumVaporPressure;
  }

  public void setEquilibriumVaporPressure(Double equilibriumVaporPressure) {
    this.equilibriumVaporPressure = equilibriumVaporPressure;
  }

  public Double getAirDensity() {
    return airDensity;
  }

  public void setAirDensity(Double airDensity) {
    this.airDensity = airDensity;
  }

  /**
   * Get field value by name. Works smoothly with autoconfiguration so the persistence manager gets
   * data only from fields that are configured in the database.
   *
   * @param fieldName name of the field
   * @return value
   */
  public Object getFieldValue(String fieldName) {
    try {
      return nameToGetter.get(fieldName).invoke(this);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  /** See {@link #getFieldValue(String fieldName)} */
  public static void enableCallFieldGetterByMethodName() {

    nameToGetter = new HashMap<>();
    for (Method m : EnhancedRuuviMeasurement.class.getMethods()) {
      if (!Void.class.equals(m.getReturnType())) {
        String name = m.getName().replace("get", "");
        name = Character.toLowerCase(name.charAt(0)) + name.substring(1);
        nameToGetter.put(name, m);
      }
    }
  }

  @Override
  public String toString() {
    return "EnhancedRuuviMeasurement{"
        + "time="
        + time
        + ", name="
        + name
        + ", mac="
        + mac
        + ", receiver="
        + receiver
        + ", rssi="
        + rssi
        + ", accelerationTotal="
        + accelerationTotal
        + ", accelerationAngleFromX="
        + accelerationAngleFromX
        + ", accelerationAngleFromY="
        + accelerationAngleFromY
        + ", accelerationAngleFromZ="
        + accelerationAngleFromZ
        + ", absoluteHumidity="
        + absoluteHumidity
        + ", dewPoint="
        + dewPoint
        + ", equilibriumVaporPressure="
        + equilibriumVaporPressure
        + ", airDensity="
        + airDensity
        + ", super="
        + super.toString()
        + '}';
  }
}
