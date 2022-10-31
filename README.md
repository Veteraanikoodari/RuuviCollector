# RuuviCollector

NOTE: this is a substantially modified version from original, intended for advanced users.
All deprecated features have been removed and only InfluxDB2 support and Prometheus export remains.

### Features

Supports following RuuviTag [Data Formats](https://github.com/ruuvi/ruuvi-sensor-protocols):

-   Data Format 5: "RAW v2" BLE Manufacturer specific data, all current sensor readings + extra

Supports following data from the tag (depending on tag firmware):

-   Temperature (Celsius)
-   Relative humidity (0-100%)
-   Air pressure (Pascal)
-   Acceleration for X, Y and Z axes (g)
-   Battery voltage (Volts)
-   TX power (dBm)
-   RSSI (Signal strength _at the receiver_, dBm)
-   Movement counter (Running counter incremented each time a motion detection interrupt is received)
-   Measurement sequence number (Running counter incremented each time a new measurement is taken on the tag)

Ability to calculate following values in addition to the raw data (the accuracy of these values are approximations):

-   Total acceleration (g)
-   Absolute humidity (g/m³)
-   Dew point (Celsius)
-   Equilibrium vapor pressure (Pascal)
-   Air density (Accounts for humidity in the air, kg/m³)
-   Acceleration angle from X, Y and Z axes (Degrees)

See [MEASUREMENTS.md](./MEASUREMENTS.md) for additional details about the measurements.

### Requirements

-   Linux-based OS (this application uses the bluez stack for Bluetooth which is not available for Windows for example)
-   Bluetooth adapter supporting Bluetooth Low Energy
-   _bluez_ and _bluez-hcidump_ at least version 5.41 (For running the application, versions prior to 5.41 have a bug which causes the bluetooth to hang occasionally while doing BLE scanning)
-   Gradle (For building from sources)
-   JDK11 (For building from sources, JRE11 is enough for just running the built JAR)

### Building

Execute

```sh
gradlew clean build
```

### Installation

#### Automatic Setup

TODO: update to docker only.

Service scripts and other necessary stuff for "properly installing" this are
available in the [service-setup](./service-setup/) directory.

#### Manual Setup

-   hcitool and hcidump require additional capabilities to be run as a normal user, so you need to execute the following commands or run the application as root

```sh
sudo setcap 'cap_net_raw,cap_net_admin+eip' `which hcitool`
sudo setcap 'cap_net_raw,cap_net_admin+eip' `which hcidump`
```

-   Run the built JAR-file with `java -jar ruuvi-collector-*.jar`
	-   Note: it's recommended to run this for example inside _screen_ to avoid the application being killed when terminal session ends
-   To configure the settings, copy the `ruuvi-collector.properties.example` to `ruuvi-collector.properties` and place it in the same directory as the JAR file and edit the file according to your needs.

### Configuration

The default configuration which works without a config file assumes InfluxDB is running locally with default settings, with a database called 'ruuvi'.
To change the default settings, copy the ruuvi-collector.properties.example file as ruuvi-collector.properties in the same directory as the collector jar file and change the settings you want.
Most up-to-date information about the supported configuration options can be found in the ruuvi-collector.properties.example file.

To give human readable friendly names to tags (based on their MAC address), copy the ruuvi-names.properties.example file as ruuvi-names.properties in the same directory as the collector jar file and set the names in this file according to the examples there.

### Running

For built version (while in the "root" of the project):

```sh
java -jar target/ruuvi-collector-*.jar
```

Easily compile and run while developing:

```
mvn compile exec:java
```

### Docker

Dockerized installation is possible with the bundled Dockerfile, which is particularly useful for "server-grade" installations. The Docker image can be built with for example:

```sh
docker build -t ruuvi-collector .
```

Note: if you have configuration files present in the current directory, they will be added to the built image. Alternatively they can be mounted inside the container while running.

Depending on the configuration, it may be necessary to use `--net=host` (to access the host network stack directly) and/or `--privileged` (to access a local BLE adapter directly), for example:

```sh
docker run --name ruuvi-collector --privileged --net=host -d ruuvi-collector
```
