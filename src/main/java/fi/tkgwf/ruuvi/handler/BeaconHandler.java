package fi.tkgwf.ruuvi.handler;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import fi.tkgwf.ruuvi.bean.HCIData;
import fi.tkgwf.ruuvi.common.bean.RuuviMeasurement;
import fi.tkgwf.ruuvi.common.parser.DataFormatParser;
import fi.tkgwf.ruuvi.common.parser.impl.AnyDataFormatParser;
import fi.tkgwf.ruuvi.config.Configuration;
import java.util.Optional;

/** Creates {@link RuuviMeasurement} instances from raw dumps from hcidump. */
public class BeaconHandler {

  private final DataFormatParser parser = new AnyDataFormatParser();

  private final Configuration cfg = Configuration.get();

  /**
   * Handles a packet and creates a {@link RuuviMeasurement} if the handler understands this packet.
   *
   * @param hciData the data parsed from hcidump
   * @return an instance of a {@link EnhancedRuuviMeasurement} if this handler can parse the packet
   */
  public Optional<EnhancedRuuviMeasurement> handle(HCIData hciData) {
    HCIData.Report.AdvertisementData adData =
        hciData.findAdvertisementDataByType(0xFF); // Manufacturer-specific data, raw dataformats
    if (adData == null) {
      adData = hciData.findAdvertisementDataByType(0x16); // Eddystone url
      if (adData == null) {
        adData = hciData.findAdvertisementDataByType(0x17); // Eddystone tlm
        if (adData == null) {
          return Optional.empty();
        }
      }
    }

    if (adData.dataBytes()[0] == (byte) 0x99 && adData.dataBytes()[1] == (byte) 0x04) {
      RuuviMeasurement measurement = parser.parse(adData.dataBytes());
      if (measurement == null) {
        return Optional.empty();
      }

      EnhancedRuuviMeasurement enhancedMeasurement = new EnhancedRuuviMeasurement(measurement);
      enhancedMeasurement.setMac(hciData.mac);
      enhancedMeasurement.setRssi(hciData.rssi);
      enhancedMeasurement.setName(cfg.sensor.macAddressToName.get(hciData.mac));
      enhancedMeasurement.setReceiver(Configuration.get().storage.receiver);
      return Optional.of(enhancedMeasurement);
    }
    // LOG.error("Data format 5 (RAWv2) is only supported format.");
    return Optional.empty();
  }
}
