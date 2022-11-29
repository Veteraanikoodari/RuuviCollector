package fi.tkgwf.ruuvi;

import fi.tkgwf.ruuvi.bean.HCIData;
import fi.tkgwf.ruuvi.config.Configuration;
import fi.tkgwf.ruuvi.handler.BeaconHandler;
import fi.tkgwf.ruuvi.service.PersistenceService;
import fi.tkgwf.ruuvi.service.PersistenceServiceException;
import fi.tkgwf.ruuvi.utils.HCIParser;
import fi.tkgwf.ruuvi.utils.MeasurementValueCalculator;
import fi.tkgwf.ruuvi.utils.Utils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {

  private final BeaconHandler beaconHandler = new BeaconHandler();

  public static void main(String[] args) {
    Main m = new Main();

    if (!m.run()) {
      log.info("Unclean exit");
      System.exit(1);
    }
    log.info("Clean exit");
  }

  private BufferedReader startHciListeners() throws IOException {
    String[] scan = Configuration.get().sensor.scanCommand.split(" ");
    if (scan.length > 0 && Utils.isNotBlank(scan[0])) {
      Process hcitool = new ProcessBuilder(scan).start();
      Runtime.getRuntime().addShutdownHook(new Thread(() -> hcitool.destroyForcibly()));
      log.debug("Starting scan with: " + Arrays.toString(scan));
    } else {
      log.debug("Skipping scan command, scan command is blank.");
    }
    String[] dump = Configuration.get().sensor.dumpCommand.split(" ");
    Process hcidump = new ProcessBuilder(dump).start();
    Runtime.getRuntime().addShutdownHook(new Thread(() -> hcidump.destroyForcibly()));
    log.debug("Starting dump with: " + Configuration.get().sensor.dumpCommand);
    return new BufferedReader(new InputStreamReader(hcidump.getInputStream()));
  }

  /**
   * Run the collector.
   *
   * @return true if the run ends gracefully, false in case of severe errors
   */
  public boolean run() {
    BufferedReader reader;
    try {
      reader = startHciListeners();
    } catch (IOException ex) {
      log.error("Failed to start hci processes", ex);
      return false;
    }
    log.info(
        "BLE listener started successfully, waiting for data... \n"
            + " If you don't get any data, check that you are able to run 'hcitool lescan'"
            + " and 'hcidump --raw' without issues");
    return run(reader, PersistenceService::new);
  }

  boolean run(
      final BufferedReader reader, Supplier<PersistenceService> persistenceServiceSupplier) {
    HCIParser parser = new HCIParser();
    boolean dataReceived = false;
    boolean healthy = false;
    try (final PersistenceService persistenceService = persistenceServiceSupplier.get()) {
      String line, latestMAC = null;
      while ((line = reader.readLine()) != null) {
        if (line.contains("device: disconnected")) {
          logDisconnectError(line);
          healthy = false;
        } else if (line.contains("No such device")) {
          logNoSuchDeviceError(line);
          healthy = false;
        }
        if (!dataReceived) {
          if (line.startsWith("> ")) {
            log.info("Successfully reading data from hcidump");
            dataReceived = true;
            healthy = true;
          } else {
            continue; // skip the unnecessary garbage at beginning containing hcidump
            // version and other junk print
          }
        }
        try {
          // Read in MAC address from first line
          log.info("About: " + line);
          if (Utils.hasMacAddress(line)) {
            latestMAC = Utils.getMacFromLine(line);
          }
          // TODO Apply Mac Address Filtering
          if (Configuration.get().sensor.isAllowedMac(latestMAC)) {
            HCIData hciData = parser.readLine(line);
            if (hciData != null) {
              beaconHandler
                  .handle(hciData)
                  .map(MeasurementValueCalculator::calculateAllValues)
                  .ifPresent(persistenceService::store);
              latestMAC = null; // "reset" the mac to null to avoid misleading MAC
              // addresses when an error happens *after* successfully
              // reading a full packet
              healthy = true;
            }
          }
        } catch (PersistenceServiceException ex) {
          log.error("PersistenceService threw exception. Cause:", ex.getCause());
          log.error("Shutting down...");
          healthy = false;
        } catch (Exception ex) {
          if (latestMAC != null) {
            log.warn(
                "Uncaught exception while handling measurements from MAC address \""
                    + latestMAC
                    + "\", if this repeats and this is not a Ruuvitag, try"
                    + " blacklisting it",
                ex);
          } else {
            log.warn(
                "Uncaught exception while handling measurements, this is an"
                    + " unexpected event. Please report this to"
                    + " https://github.com/Scrin/RuuviCollector/issues and include"
                    + " this log",
                ex);
          }
          log.debug("Offending line: " + line);
        }
      }
    } catch (IOException ex) {
      log.error("Uncaught exception while reading measurements", ex);
      return false;
    }
    return healthy;
  }

  private void logDisconnectError(String line) {
    log.error(
        line
            + ": Either the bluetooth device was externally disabled or"
            + " physically disconnected");
  }

  private void logNoSuchDeviceError(String line) {
    log.error(line + ": Check that your bluetooth adapter is enabled and working" + " properly");
  }
}
