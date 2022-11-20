package fi.tkgwf.ruuvi.timescaleDB;

import fi.tkgwf.ruuvi.db.TimescaleDBConnection;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.TimescaleDBContainerProvider;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.SQLException;

@Testcontainers
class TimescaleDBTest {

    private static TimescaleDBConnection tdbc;

    @Container
    private static JdbcDatabaseContainer container = new TimescaleDBContainerProvider()
        .newInstance().withDatabaseName("ruuvi");

    @BeforeAll
    static void init() throws SQLException {
        container.start();
        tdbc = TimescaleDBConnection.from(container.createConnection(""));
    }

    @Test
    void testSomething() {
        var mapped = container.getMappedPort(5432);
        System.out.println(mapped);
    }
}
