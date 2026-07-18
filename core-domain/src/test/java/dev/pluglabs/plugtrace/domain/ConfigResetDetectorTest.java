package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigResetDetectorTest {
    @Test
    void requiresBothFortyPercentKeyAndSizeLoss() {
        ConfigSnapshot before = new ConfigSnapshot("Shop", "config.yml", "a", "structure", 1000, 100);

        assertTrue(ConfigResetDetector.possibleReset(before,
                new ConfigSnapshot("Shop", "config.yml", "b", "structure", 500, 50)));
        assertFalse(ConfigResetDetector.possibleReset(before,
                new ConfigSnapshot("Shop", "config.yml", "c", "structure", 900, 50)));
        assertFalse(ConfigResetDetector.possibleReset(before,
                new ConfigSnapshot("Shop", "config.yml", "d", "structure", 500, 90)));
    }
}
