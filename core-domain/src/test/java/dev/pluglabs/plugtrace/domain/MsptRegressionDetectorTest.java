package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MsptRegressionDetectorTest {
    @Test
    void usesTheMedianAndRequiresBothAbsoluteAndRelativeThresholds() {
        assertEquals(20.0, MsptRegressionDetector.median(List.of(18.0, 22.0, 20.0)));
        assertEquals(20.0, MsptRegressionDetector.median(List.of(18.0, 19.0, 21.0, 22.0)));

        assertTrue(MsptRegressionDetector.material(20.0, 30.0));
        assertFalse(MsptRegressionDetector.material(25.0, 35.0));
        assertFalse(MsptRegressionDetector.material(20.0, 29.99));
        assertFalse(MsptRegressionDetector.material(0.0, 30.0));
    }

    @Test
    void createsAWarningThatRecommendsSparkWithoutNamingASuspect() {
        CheckResult result = MsptRegressionDetector.check(20.0, List.of(29.0, 30.0, 31.0));

        assertEquals(CheckStatus.WARN, result.status());
        assertEquals(CheckCriticality.WARNING, result.criticality());
        assertTrue(result.summary().toLowerCase().contains("spark"));
        assertEquals(20.0, result.safeDetails().get("baselineMedianMspt"));
        assertEquals(30.0, result.safeDetails().get("currentMedianMspt"));
        assertFalse(result.safeDetails().containsKey("suspect"));
    }

    @Test
    void reportsUnknownUntilACompleteWindowAndBaselineExist() {
        assertEquals(CheckStatus.UNKNOWN, MsptRegressionDetector.check(null, List.of(20.0)).status());
        assertEquals(CheckStatus.UNKNOWN, MsptRegressionDetector.check(20.0, List.of()).status());
    }
}
