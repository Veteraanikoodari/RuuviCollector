package fi.tkgwf.ruuvi.utils;

import static org.junit.jupiter.api.Assertions.assertEquals;

import fi.tkgwf.ruuvi.TestDataFactory;
import fi.tkgwf.ruuvi.bean.HCIData;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class HCIParserTest {

  @Test
  void assertAllFields() {
    final HCIData hciData = new HCIParser().readLine(TestDataFactory.getDataFormat5Msg());
    System.out.println(hciData);
    assertEquals(4, hciData.packetType.intValue());
    assertEquals(62, hciData.eventCode.intValue());
    assertEquals(43, hciData.packetLength.intValue());
    assertEquals(2, hciData.subEvent.intValue());
    assertEquals(1, hciData.numberOfReports.intValue());
    assertEquals(0, hciData.eventType.intValue());
    assertEquals(1, hciData.peerAddressType.intValue());
    assertEquals("FE1E8AADD7BF", hciData.mac);
    assertEquals(-79, hciData.rssi.intValue());

    assertEquals(31, hciData.reports.get(0).length.intValue());
    assertEquals(1, hciData.reports.size());

    assertEquals(2, hciData.reports.get(0).advertisements.size());
    assertEquals(2, hciData.reports.get(0).advertisements.get(0).length.intValue());
    assertEquals(1, hciData.reports.get(0).advertisements.get(0).type.intValue());
    assertEquals(Arrays.asList((byte) 6), hciData.reports.get(0).advertisements.get(0).data);

    assertEquals(27, hciData.reports.get(0).advertisements.get(1).length.intValue());
    assertEquals(255, hciData.reports.get(0).advertisements.get(1).type.intValue());
    assertEquals(
        Arrays.asList(
            (byte) -103,
            (byte) 4,
            (byte) 5,
            (byte) 17,
            (byte) 23,
            (byte) 52,
            (byte) -81,
            (byte) -50,
            (byte) -30,
            (byte) 3,
            (byte) -8,
            (byte) -1,
            (byte) -24,
            (byte) -1,
            (byte) -40,
            (byte) -80,
            (byte) -74,
            (byte) 77,
            (byte) 97,
            (byte) 49,
            (byte) -2,
            (byte) 30,
            (byte) -118,
            (byte) -83,
            (byte) -41,
            (byte) -65),
        hciData.reports.get(0).advertisements.get(1).data);
  }
}
