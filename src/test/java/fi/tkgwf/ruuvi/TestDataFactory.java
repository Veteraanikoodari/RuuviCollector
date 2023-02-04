package fi.tkgwf.ruuvi;

import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.handler.BeaconHandler;
import fi.tkgwf.ruuvi.utils.HCIParser;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

public class TestDataFactory {
    public static String getDataFormat5Msg() {
//                                                       Another length something?
//                                                       |  BLE advertisement, data type and flags
//                                                       |  |         Payload length (27 bytes, not counting 'self')
//                                                       |  |        |  Manufacturer specific data (Type)
//                                                       |  |        |  |  Manufacturer ID 0x0499 (Ruuvi Innovations Ltd)
//                                                       |  |        |  |  |     Data format definition (5)                            MAC address (again..)
//                HCI_EVT                                |  |        |  |  |     |  Temperature (-163.835 °C to +163.835 °C in 0.005 °C incr)         |
//                |  PDU header                          |  |        |  |  |     |  |     Humidity (Humidity (16bit unsigned) in 0.0025%)
//                |  |  Packet length (Format 5)         |  |        |  |  |     |  |     |     Pressure (16bit unsigned in 1Pa units, offset of -50 000 Pa)
//                |  |  |  subEvent 02 LE Advertising report|        |  |  |     |  |     |     |     Acceleration-X (2 bytes)         |
//                |  |  |  |  Number of reports          |  |        |  |  |     |  |     |     |     |     Acceleration-Y (2 bytes)   |
//                |  |  |  |  |  Event type              |  |        |  |  |     |  |     |     |     |     |     Acceleration-Z (2 bytes)
//                |  |  |  |  |  |  Random device address|  |        |  |  |     |  |     |     |     |     |     |     Battery power & TX power
//                |  |  |  |  |  |  |  MAC address (6 bytes)|        |  |  |     |  |     |     |     |     |     |     |     Movement counter           RSSI
//                |  |  |  |  |  |  |  |                 |  |        |  |  |     |  |     |     |     |     |     |     |     |  MSQN  |                 |
        return "> 04 3E 2B 02 01 00 01 BF D7 AD 8A 1E FE 1F 02 01 06 1B FF 99 04 05 11 17 34 AF CE E2 03 F8 FF E8 FF D8 B0 B6 4D 61 31 FE 1E 8A AD D7 BF B1";
    }

    public static String getDataFormat5Msg(String mac, double tempC) {
        String temperature = tempCToHex(tempC);
        var macList = Arrays.asList(mac.split(" "));
        Collections.reverse(macList);
        var reverseMac = String.join(" ", macList);
        Arrays.stream(mac.split(" ")).map(str -> new StringBuffer(str).reverse().toString());

        var str = "> 04 3E 2B 02 01 00 01 "
        + "%s 1F 02 01 06 1B FF 99 04 05 %s 34 AF CE E2 03 F8 FF E8 FF D8 B0 B6 4D 61 31 %s B1";
        return String.format(str, reverseMac, temperature, mac);
    }

    private static String tempCToHex(double tmp) {
        var temp = tmp / 0.005;
        var hex = Integer.toHexString((int)temp);

        var leadZeroCount = 4 - hex.length();
        if (leadZeroCount > 0) {
            hex = String.format("%0" + leadZeroCount + "d", 0) + hex;
        }
        return hex.substring(0, 2) + " " + hex.substring(2, 4);
    }

    public static void main(String[] args) {
        var hciData = new HCIParser().readLine(getDataFormat5Msg("AA BB CC DD EE FF", 110.15));
        var em = new BeaconHandler().handle(hciData);
        System.out.println(em.get().toString());

        hciData = new HCIParser().readLine(getDataFormat5Msg());
        em = new BeaconHandler().handle(hciData);
        System.out.println(em);
    }

    public static String getConfiguredMacFromCollection() {
        var set = Configuration.get().getConfiguredMacAddresses();
        var idx =new Random(System.currentTimeMillis()).nextInt(set.size());
        var counter = 0;
        while(set.iterator().hasNext()) {
            if (counter++ == idx) {
                return set.iterator().next();
            }
        }
        return null;
    }

    public static String getRandomMacFromCollection() {
        var tmp = List.of("A1", "A2", "A3");
        var idx =new Random(System.currentTimeMillis()).nextInt(3);
        return "XX XX XX XX XX XX".replaceAll("XX", tmp.get(idx));
    }

    public static List<Long> getMeasurementTimes(long startTime, int count, int intervalInMs) {
        return LongStream.iterate(startTime, i -> i + intervalInMs)
            .limit(count)
            .boxed()
            .collect(Collectors.toList());
    }
}

/*
* Example hcidump --raw
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A B6
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B5
> 04 3E 2B 02 01 00 01 BF D7 AD 8A 1E FE 1F 02 01 06 1B FF 99
  04 05 11 17 34 AF CE E2 03 F8 FF E8 FF D8 B0 B6 4D 61 31 FE
  1E 8A AD D7 BF B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 2B 02 01 00 01 FB 9C 2B BC BD CC 1F 02 01 06 1B FF 99
  04 05 12 08 34 35 CF 17 03 DC FF 00 FF CC AB 76 77 60 66 CC
  BD BC 2B 9C FB B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 B6
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2

  * RUUVI F5
> 04 3E 2B 02 01 00 01 52 D8 8F CE 65 D2 1F 02 01 06 1B FF 99
  04 05 10 68 37 5A CF 17 04 0C FF 50 00 34 A3 F6 83 12 3E D2
  65 CE 8F D8 52 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B0
> 04 3E 18 02 01 00 00 A5 2F 25 85 9C 78 0C 02 01 06 03 02 24
  FE 04 FF D1 01 01 AA
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2

  * RUUVI F5
> 04 3E 2B 02 01 00 01 30 4E A4 16 61 E8 1F 02 01 06 1B FF 99
  04 05 FE 65 89 C1 CF 1D 01 90 03 D0 00 0C 94 F6 EE 62 75 E8
  61 16 A4 4E 30 B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B0
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 B7
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B4
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B4
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A AC
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B0
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 B8
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A B6

  * RUUVI F5
> 04 3E 2B 02 01 00 01 EA 59 EC D2 BB CC 1F 02 01 06 1B FF 99
  04 05 11 88 36 E2 CE EE 03 D4 00 24 FF D0 AE F6 1E 61 93 CC
  BB D2 EC 59 EA A3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B0
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B4
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 AE

  * RUUVI F5
> 04 3E 2B 02 01 00 01 94 2E 38 85 A0 D7 1F 02 01 06 1B FF 99
  04 05 11 67 FF FF FF FF 03 AC FE 6C 00 20 AB 76 F8 62 25 D7
  A0 85 38 2E 94 A5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A A8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1

  * * RUUVI F5
> 04 3E 2B 02 01 00 01 3B A7 B1 44 97 F1 1F 02 01 06 1B FF 99
  04 05 01 E3 6F 2B CF 32 03 D8 FE 70 FF FC 81 36 CB D2 FD F1
  97 44 B1 A7 3B AA
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B6

  * * RUUVI F5
> 04 3E 2B 02 01 00 01 BF D7 AD 8A 1E FE 1F 02 01 06 1B FF 99
  04 05 11 17 34 AF CE E2 03 F8 FF E8 FF D8 B0 B6 4D 61 31 FE
  1E 8A AD D7 BF B0
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B4
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3

  * * RUUVI F5
> 04 3E 2B 02 01 00 01 FB 9C 2B BC BD CC 1F 02 01 06 1B FF 99
  04 05 12 06 34 38 CF 14 03 E8 FF 08 FF CC AB 76 77 60 67 CC
  BD BC 2B 9C FB B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B4
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 B8
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
  * * RUUVI F5
> 04 3E 2B 02 01 00 01 30 4E A4 16 61 E8 1F 02 01 06 1B FF 99
  04 05 FE 61 89 BA CF 20 01 88 03 D0 00 0C 94 F6 EE 62 76 E8
  61 16 A4 4E 30 BA
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B0
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B5
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 B7
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A B5
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 AA
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B4
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B3
> 04 3E 18 02 01 00 00 A5 2F 25 85 9C 78 0C 02 01 06 03 02 24
  FE 04 FF D1 01 01 A9
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B0
> 04 3E 2B 02 01 00 01 EA 59 EC D2 BB CC 1F 02 01 06 1B FF 99
  04 05 11 88 36 E2 CE EE 03 D4 00 24 FF D0 AE F6 1E 61 93 CC
  BB D2 EC 59 EA A4
> 04 3E 1B 02 01 03 01 34 29 E1 9C 96 0C 0F 02 01 1A 0B FF 4C
  00 09 06 03 03 C0 A8 01 7A B7
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B2
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1D 02 01 00 01 90 2A B7 FA 3D 50 11 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 02 0A 00 AD
> 04 3E 27 02 01 00 00 B0 57 DF 50 A0 00 1B 02 01 06 17 09 48
  77 5A 5F 66 62 31 35 32 65 36 62 36 66 30 30 32 62 31 65 33
  30 B1
> 04 3E 1A 02 01 00 01 B9 6E 6F DC 79 5B 0E 02 01 1A 0A FF 4C
  00 10 05 4E 1C 53 F5 28 AB
*
* */
