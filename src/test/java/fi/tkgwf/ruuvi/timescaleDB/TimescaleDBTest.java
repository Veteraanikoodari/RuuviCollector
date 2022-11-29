package fi.tkgwf.ruuvi.timescaleDB;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.common.bean.RuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.db.TimescaleDBConnection;
import java.sql.SQLException;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.postgresql.util.PSQLException;

class TimescaleDBTest extends TimescaleTestBase {

  @Test
  @DisplayName("Assert that autoconfiguration runs without errors when called multiple times.")
  void testAutoconfiguration() {
    assertDoesNotThrow(() -> timescale.autoConfigure());
  }

  @Test
  @DisplayName("Assert that grafana user has only read privileges to database.")
  void testNoWriteAccessForGrafanaUser() throws SQLException {
    var user = Configuration.get().timescaleDB.grafanaUser;
    var pwd = Configuration.get().timescaleDB.grafanaPwd;
    var con = container.withUsername(user).withPassword(pwd).createConnection("");
    var grafanaCon = TimescaleDBConnection.from(con);
    // The first thing timescaleDBConnection does with new MAC address, is storing it in sensor
    // table
    // before processing other data. So no need for more data.
    var enhancedMeasurement = new EnhancedRuuviMeasurement(new RuuviMeasurement());
    enhancedMeasurement.setMac("A1B1C1D1E1F1");

    var ex =
        Assertions.assertThrows(RuntimeException.class, () -> grafanaCon.save(enhancedMeasurement));
    Assertions.assertEquals(PSQLException.class, ex.getCause().getClass());
    Assertions.assertTrue(ex.getCause().getMessage().contains("permission denied"));
  }
}
