package fi.tkgwf.ruuvi.timescaleDB;

import fi.tkgwf.ruuvi.db.TimescaleDBConnection;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.TimescaleDBContainerProvider;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
public abstract class TimescaleTestBase {
  protected static TimescaleDBConnection timescale;

  @Container
  protected static final JdbcDatabaseContainer<?> container =
      new TimescaleDBContainerProvider().newInstance().withDatabaseName("ruuvi");

  @BeforeAll
  static void initAll() throws SQLException {
    container.start();
    timescale = TimescaleDBConnection.from(container.createConnection("")).autoConfigure();
  }
}
