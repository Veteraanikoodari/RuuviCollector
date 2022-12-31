package fi.tkgwf.ruuvi.timescaleDB;

import static fi.tkgwf.ruuvi.TestDataFactory.getMeasurementTimes;
import static org.junit.jupiter.api.Assertions.*;

import fi.tkgwf.ruuvi.FixedInstantsProvider;
import fi.tkgwf.ruuvi.TestDataFactory;
import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.common.bean.RuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.db.TimescaleDBConnection;
import fi.tkgwf.ruuvi.service.PersistenceServiceException;
import fi.tkgwf.ruuvi.utils.Utils;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@Slf4j
class TimescaleDBTest extends TimescaleTestBase {

  @Test
  @DisplayName("Assert that autoconfiguration runs without errors when called multiple times.")
  void testAutoconfiguration() {
    assertDoesNotThrow(() -> timescale.autoConfigure());
  }

  @Test
  @DisplayName("Assert that grafana user has only read privileges to database.")
  void testNoWriteAccessForGrafanaUser() throws SQLException {
    Utils.setCurrentTimeMillisSupplier(System::currentTimeMillis);
    var user = Configuration.get().timescaleDB.grafanaUser;
    var pwd = Configuration.get().timescaleDB.grafanaPwd;
    Configuration.get().timescaleDB.batchSize = 1;
    var con = container.withUsername(user).withPassword(pwd).createConnection("");
    var grafanaCon = TimescaleDBConnection.from(con);
    //
    var enhancedMeasurement = new EnhancedRuuviMeasurement(new RuuviMeasurement());
    enhancedMeasurement.setMac("A1B1C1D1E1F1");

    var ex =
        Assertions.assertThrows(
            PersistenceServiceException.class, () -> grafanaCon.save(enhancedMeasurement));
    Assertions.assertEquals(BatchUpdateException.class, ex.getCause().getClass());
    Assertions.assertTrue(
        ex.getCause().getMessage().contains("permission denied"), ex.getCause().getMessage());
  }

  @Test
  @DisplayName("Assert that real-time aggregate and data retention policies work as expected")
  void testAggregateAndDataRetention() throws SQLException {
    int sampleCount = 1000;
    int sampleIntervalMs = 10000;
    Configuration.get().timescaleDB.batchSize = 10;

    // Generate 1000 samples between 10000ms intervals, starting from two months ago.
    // we should get about 15..20 measurements downsampled. (16.666 samples)
    assertRtaResults(
        sampleCount,
        sampleIntervalMs,
        OffsetDateTime.now().minusMonths(2).toInstant().toEpochMilli(),
        15,
        20);

    // Generate results beyond retention policy to demonstrate that 'late arrivals' go through the
    // main
    // table and are aggregated before being evicted.
    assertRtaResults(
        sampleCount,
        sampleIntervalMs,
        OffsetDateTime.now().minusMonths(3).toInstant().toEpochMilli(),
        30,
        40);

    // Generate results before aggregation period to demonstrate that aggregated table still shows
    // downsampled numbers instead of 'real time' values. It's just that the results are recent.
    assertRtaResults(
        sampleCount,
        sampleIntervalMs,
        OffsetDateTime.now().minusWeeks(1).toInstant().toEpochMilli(),
        45,
        60);
  }

  @Test
  void testOnlyConfiguredSensorsAccepted() {
    Utils.setCurrentTimeMillisSupplier(System::currentTimeMillis);
    Configuration.get().timescaleDB.batchSize = 1;

    var measurement = getMeasurements(1).get(0);
    measurement.setMac("FFFFFFFFFFFF");
    timescale.save(measurement);
    assertEquals(0, timescale.getTotalMeasurementWrites());

    // assert configured sensor reading gets through
    measurement.setMac("A1A1A1A1A1A1");
    timescale.save(measurement);
    assertEquals(1, timescale.getTotalMeasurementWrites());
  }

  private void assertRtaResults(
      int sampleCount, int sampleInterval, long startTime, int lowLimit, int highLimit) {
    // Configure time supplier to produce times that start from three months in the past.
    Utils.setCurrentTimeMillisSupplier(
        FixedInstantsProvider.from(getMeasurementTimes(startTime, sampleCount, sampleInterval)));
    getMeasurements(sampleCount).forEach(erm -> timescale.save(erm));
    // when we query the amount of measurements from RTA table we should get the downsampled number.
    var downSampledCount = getMeasurementCount("measurement_five_minutes");
    log.info("read {} measurements", downSampledCount);
    assertBetween(downSampledCount, lowLimit, highLimit);
  }

  @SneakyThrows
  private int getMeasurementCount(String table) {
    var con = container.createConnection("");
    var qry = "SELECT count(*) from " + table;
    var rs = con.createStatement().executeQuery(qry);
    rs.next();
    var result = rs.getInt(1);
    con.close();
    return result;
  }

  private List<EnhancedRuuviMeasurement> getMeasurements(int count) {

    List<EnhancedRuuviMeasurement> list = new ArrayList<>();
    for (int i = 0; i < count; i++) {
      var erm = new EnhancedRuuviMeasurement();
      erm.setAbsoluteHumidity(40d);
      erm.setAccelerationTotal(9.81d);
      erm.setAirDensity(10d);
      erm.setAccelerationX(4d);
      erm.setAccelerationY(4d);
      erm.setAccelerationZ(4d);
      erm.setAccelerationAngleFromX(4d);
      erm.setAccelerationAngleFromY(4d);
      erm.setAccelerationAngleFromZ(4d);
      erm.setDewPoint(40d);
      erm.setHumidity(40d);
      erm.setEquilibriumVaporPressure(100d);
      erm.setBatteryVoltage(2d);
      erm.setTxPower(20);
      erm.setRssi(2);
      erm.setMeasurementSequenceNumber(i);
      erm.setMovementCounter(i);
      erm.setPressure(1000d);
      erm.setTemperature(20d);
      erm.setMac(TestDataFactory.getConfiguredMacFromCollection());
      list.add(erm);
    }
    return list;
  }

  private void assertBetween(int value, int from, int to) {
    assertTrue(
        from <= value && value <= to,
        String.format("Expected %d < X < %d, but X was %d", from, to, value));
  }
}
