package fi.tkgwf.ruuvi;

import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.db.TimescaleDBUtil;
import fi.tkgwf.ruuvi.service.PersistenceService;
import fi.tkgwf.ruuvi.timescaleDB.TimescaleTestBase;
import fi.tkgwf.ruuvi.utils.Pair;
import fi.tkgwf.ruuvi.utils.Utils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.HashSet;

import static fi.tkgwf.ruuvi.TestDataFactory.getMeasurementTimes;
import static fi.tkgwf.ruuvi.db.TimescaleDBUtil.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IntegrationTest extends TimescaleTestBase {

  private static final int configuredSensorCount = 3;

  private Main main = new Main();

  @Test
  @DisplayName(
      "Generate 10 readings at measurement update limit intervals and confirm from storage.")
  void testGeneratedBleDataIsStoredAsExpected() throws SQLException {

    var updateLimit = Configuration.get().sensorDefaults.measurementUpdateLimitMs;
    var sampleSize = Configuration.get().timescaleDB.batchSize;
    // Configure time supplier (with extra buffer), so that our measurements
    // are not discarded because of too high frequency
    Utils.setCurrentTimeMillisSupplier(
        FixedInstantsProvider.from(getMeasurementTimes(0, sampleSize * 3, (int) updateLimit)));

    var testData = getTestString(sampleSize);
    final var persistenceService = new PersistenceService(timescale);
    var reader = new BufferedReader(new StringReader(testData.getLeft()));
    assertTrue(main.run(reader, () -> persistenceService));

    var con = container.createConnection("");
    // Confirm measurement..
    assertRowCount(con, MEASUREMENT_TBL, sampleSize);
    assertRowCount(con, SENSOR_TBL, configuredSensorCount);
    assertRowCount(con, SENSOR_LOCATION_TBL, configuredSensorCount);
    assertRowCount(con, LOCATION_TBL, configuredSensorCount);
  }

  private void assertRowCount(Connection con, String table, int expected) throws SQLException {
    // Confirm measurement..
    var rs = con.createStatement().executeQuery("SELECT count(*) FROM " + table);
    assertTrue(rs.next());
    assertEquals(expected, rs.getInt(1));
    rs.close();
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
