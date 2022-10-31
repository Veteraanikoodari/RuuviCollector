package fi.tkgwf.ruuvi.db;

import fi.tkgwf.ruuvi.bean.EnhancedRuuviMeasurement;
import org.apache.log4j.Logger;

public class DummyDBConnection implements RuuviDBConnection {

    private static final Logger LOG = Logger.getLogger(DummyDBConnection.class);

    @Override
    public void save(EnhancedRuuviMeasurement measurement) {
        LOG.debug(measurement);
    }

    @Override
    public void close() {}
}
