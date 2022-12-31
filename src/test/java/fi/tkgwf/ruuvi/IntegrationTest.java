package fi.tkgwf.ruuvi;

import static fi.tkgwf.ruuvi.TestDataFactory.getMeasurementTimes;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.service.PersistenceService;
import fi.tkgwf.ruuvi.timescaleDB.TimescaleTestBase;
import fi.tkgwf.ruuvi.utils.Pair;
import fi.tkgwf.ruuvi.utils.Utils;
import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.HashSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

public class IntegrationTest extends TimescaleTestBase {
  Main main = new Main();

  @Test
  @DisplayName(
      "Generate 10 readings at measurement update limit intervals and confirm from storage.")
  void testGeneratedBleDataIsStoredAsExpected() throws SQLException {

    var updateLimit = Configuration.get().sensorDefaults.measurementUpdateLimitMs;
    var sampleSize = Configuration.get().timescaleDB.batchSize;
    // Configure time supplier, so that our measurements are not discarded
    // because of too high frequency
    Utils.setCurrentTimeMillisSupplier(
        FixedInstantsProvider.from(getMeasurementTimes(0, sampleSize * 2 + 2, (int) updateLimit)));

    var testData = getTestString(sampleSize);
    final var persistenceService = new PersistenceService(timescale);
    var reader = new BufferedReader(new StringReader(testData.getLeft()));
    assertTrue(main.run(reader, () -> persistenceService));

    var con = container.createConnection("");
    // Confirm measurement..
    var rs = con.createStatement().executeQuery("SELECT count(*) FROM measurement");
    assertTrue(rs.next());
    assertEquals(sampleSize, rs.getInt(1));
    // ..and sensor table sizes.
    rs = con.createStatement().executeQuery("SELECT count(*) FROM sensor");
    assertTrue(rs.next());
    assertEquals(testData.getRight(), rs.getInt(1));
  }

  /** Get test String with sampleSize measurements and count of distinct mac addresses used. */
  private static Pair<String, Integer> getTestString(int sampleSize) {
    var selectedMacs = new HashSet<String>();
    var sb = new StringBuilder("Ignorable garbage at the start\n");
    for (int i = 1; i < sampleSize + 1; i++) {
      var mac = TestDataFactory.getRandomMacFromCollection();
      selectedMacs.add(mac);
      var msg = TestDataFactory.getDataFormat5Msg(mac, i);
      sb.append(msg).append("\n");
    }
    return Pair.of(sb.toString(), selectedMacs.size());
  }
}
