package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SparkDetectorTest {
    @Test
    void recognizesPapersBundledSparkFromItsRegisteredHelpTopic() {
        SparkDetector.Detection detection = SparkDetector.decide(false, null, true);

        assertTrue(detection.detected());
        assertEquals("bundled", detection.version());
    }

    @Test
    void prefersAnEnabledPluginVersionAndFailsClosedWhenNeitherSignalExists() {
        SparkDetector.Detection plugin = SparkDetector.decide(true, "1.10.142", true);
        SparkDetector.Detection absent = SparkDetector.decide(false, null, false);

        assertTrue(plugin.detected());
        assertEquals("1.10.142", plugin.version());
        assertFalse(absent.detected());
    }
}
