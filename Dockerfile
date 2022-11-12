FROM eclipse-temurin:11.0.17_8-jre
RUN apt-get update
RUN apt-get -y install bluez
RUN apt-get -y install bluez-hcidump
COPY ruuvi-collector-0.2-all.jar ruuvi-collector-1.0.1.jar
ENTRYPOINT ["java","-jar","/ruuvi-collector-1.0.1.jar"]
