package fi.tkgwf.ruuvi.db;

import com.influxdb.client.*;
import com.influxdb.client.write.Point;
import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.config.Config;
import fi.tkgwf.ruuvi.utils.InfluxDB2Converter;

public class InfluxDB2Connection implements DBConnection
{
  private final InfluxDBClient client;
  private final WriteApiBlocking writeApiBlocking;
  private final WriteApi writeApi;

  public InfluxDB2Connection() {
    this(Config.getInfluxUrl(),
         Config.getInfluxToken().toCharArray(),
         Config.getInfluxOrg(),
         Config.getInfluxBucket(),
         Config.isInfluxGzip(),
         Config.isInfluxBatch(),
         Config.getInfluxBatchMaxSize(),
         Config.getInfluxBatchMaxTimeMs());
  }

  public InfluxDB2Connection(String influxUrl, char[] influxToken, String influxOrg, String influxBucket,
                             boolean gzip, boolean batch, int batchSize, int batchTimeMs) {
    client = InfluxDBClientFactory.create(influxUrl, influxToken, influxOrg, influxBucket);
    if (gzip) {
      client.enableGzip();
    } else {
      client.disableGzip();
    }

    if (batch) {
      writeApiBlocking = null;
      writeApi = client.makeWriteApi(WriteOptions.builder()
                                       .batchSize(batchSize)
                                       .flushInterval(batchTimeMs)
                                       .build());
    } else {
      writeApi = null;
      writeApiBlocking = client.getWriteApiBlocking();
    }
  }

  @Override
  public void save(EnhancedRuuviMeasurement measurement) {
    Point point = InfluxDB2Converter.toInflux(measurement);
    if (writeApi != null) {
      writeApi.writePoint(point);
    } else {
      writeApiBlocking.writePoint(point);
    }
  }

  @Override
  public void close() {
    client.close();
  }
}
