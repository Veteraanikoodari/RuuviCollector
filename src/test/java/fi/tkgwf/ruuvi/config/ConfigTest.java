package fi.tkgwf.ruuvi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import fi.tkgwf.ruuvi.strategy.impl.DefaultDiscardingWithMotionSensitivityStrategy;
import fi.tkgwf.ruuvi.strategy.impl.DiscardUntilEnoughTimeHasElapsedStrategy;
import java.io.File;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.Properties;
import java.util.function.Function;
import java.util.function.Predicate;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ConfigTest {

    public static Function<String, File> configTestFileFinder() {
        return propertiesFileName ->
                Optional.ofNullable(
                                Config.class.getResource(String.format("/%s", propertiesFileName)))
                        .map(
                                url -> {
                                    try {
                                        return url.toURI();
                                    } catch (final URISyntaxException e) {
                                        throw new RuntimeException(e);
                                    }
                                })
                        .map(File::new)
                        .orElse(null);
    }

    @BeforeEach
    void resetConfigBefore() {
        Config.reload(configTestFileFinder());
    }

    @AfterAll
    static void resetConfigAfter() {
        Config.reload(configTestFileFinder());
    }

    @Test
    void testNameThatCanBeFound() {
        assertEquals("Some named tag", Config.getTagName("AB12CD34EF56"));
    }

    @Test
    void testNameThatCanNotBeFound() {
        assertNull(Config.getTagName("123456789012"));
    }

}
