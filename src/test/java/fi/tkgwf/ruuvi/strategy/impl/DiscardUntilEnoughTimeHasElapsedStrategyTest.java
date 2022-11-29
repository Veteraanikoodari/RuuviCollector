package fi.tkgwf.ruuvi.strategy.impl;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fi.tkgwf.ruuvi.TestDataFactory;
import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.bean.HCIData;
import fi.tkgwf.ruuvi.handler.BeaconHandler;
import fi.tkgwf.ruuvi.utils.HCIParser;
import fi.tkgwf.ruuvi.utils.Utils;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;

class DiscardUntilEnoughTimeHasElapsedStrategyTest {

  @AfterAll
  static void restoreClock() {
    Utils.resetCurrentTimeMillisSupplier();
  }

  @Test
  void testDiscardingOfMeasurementsUntilEnoughTimeHasPassedSincePreviousMeasurement() {
    final HCIData hciData = new HCIParser().readLine(TestDataFactory.getDataFormat5Msg());
    final HCIData hciData2 = new HCIParser().readLine(TestDataFactory.getDataFormat5Msg());
    hciData2.mac = "112233445566";

    setClockToMilliseconds(0);
    final BeaconHandler v3 = new BeaconHandler();

    final var strategy = new DiscardUntilEnoughTimeHasElapsedStrategy();
    final var strategy2 = new DiscardUntilEnoughTimeHasElapsedStrategy();

    assertTrue(strategy.apply(withRssi(v3.handle(hciData).get(), 1)).isPresent());
    assertFalse(strategy.apply(withRssi(v3.handle(hciData).get(), 2)).isPresent());
    setClockToMilliseconds(1000);
    assertFalse(strategy.apply(withRssi(v3.handle(hciData).get(), 3)).isPresent());
    setClockToMilliseconds(3000);
    assertFalse(strategy.apply(withRssi(v3.handle(hciData).get(), 4)).isPresent());
    assertTrue(strategy2.apply(withRssi(v3.handle(hciData2).get(), 5)).isPresent());
    setClockToMilliseconds(6000);
    assertFalse(strategy.apply(withRssi(v3.handle(hciData).get(), 6)).isPresent());
    setClockToMilliseconds(9000);
    assertFalse(strategy.apply(withRssi(v3.handle(hciData).get(), 7)).isPresent());
    setClockToMilliseconds(10000);
    assertTrue(strategy.apply(withRssi(v3.handle(hciData).get(), 8)).isPresent());
    assertFalse(strategy2.apply(withRssi(v3.handle(hciData2).get(), 9)).isPresent());
    setClockToMilliseconds(90000);
    assertTrue(strategy.apply(withRssi(v3.handle(hciData).get(), 10)).isPresent());
    assertTrue(strategy2.apply(withRssi(v3.handle(hciData2).get(), 11)).isPresent());
  }

  private static EnhancedRuuviMeasurement withRssi(
      final EnhancedRuuviMeasurement measurement, final int rssi) {
    measurement.setRssi(rssi);
    return measurement;
  }

  private void setClockToMilliseconds(final long millis) {
    Utils.setCurrentTimeMillisSupplier(() -> millis);
  }
}
