package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StartupTimeRegressionDetectorTest {
    @Test void requiresBothTenSecondsAndFiftyPercent() {
        assertTrue(StartupTimeRegressionDetector.material(20_000, 30_000));
        assertFalse(StartupTimeRegressionDetector.material(25_000, 35_000));
        assertFalse(StartupTimeRegressionDetector.material(20_000, 29_999));
        assertFalse(StartupTimeRegressionDetector.material(0, 30_000));
    }
}
