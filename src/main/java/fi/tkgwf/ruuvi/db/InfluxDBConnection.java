package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.utils.InfluxDBConverter;
import java.util.concurrent.TimeUnit;
import org.influxdb.InfluxDB;
import org.influxdb.InfluxDBFactory;
import org.influxdb.dto.Point;

public class InfluxDBConnection implements RuuviDBConnection {

  private final InfluxDB influxDB;

  private static final Configuration cfg = Configuration.get();

  public InfluxDBConnection() {
    this(
        cfg.influxCommon.url,
        cfg.influxDB.user,
        cfg.influxDB.pwd,
        cfg.influxDB.database,
        cfg.influxCommon.retentionPolicy,
        cfg.influxCommon.gzip,
        cfg.influxCommon.batch,
        cfg.influxCommon.batchMaxSize,
        cfg.influxCommon.batchMaxTimeMs);
  }

  public InfluxDBConnection(
      String url,
      String user,
      String password,
      String database,
      String retentionPolicy,
      boolean gzip,
      boolean batch,
      int batchSize,
      int batchTime) {
    influxDB =
        InfluxDBFactory.connect(url, user, password)
            .setDatabase(database)
            .setRetentionPolicy(retentionPolicy);
    if (gzip) {
      influxDB.enableGzip();
    } else {
      influxDB.disableGzip();
    }
    if (batch) {
      influxDB.enableBatch(batchSize, batchTime, TimeUnit.MILLISECONDS);
    } else {
      influxDB.disableBatch();
    }
  }

  @Override
  public void save(EnhancedRuuviMeasurement measurement) {
    Point point = InfluxDBConverter.toInflux(measurement);
    influxDB.write(point);
  }

  @Override
  public void close() {
    influxDB.close();
  }
}
