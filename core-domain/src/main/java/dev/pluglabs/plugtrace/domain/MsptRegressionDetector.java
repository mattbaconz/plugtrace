package dev.pluglabs.plugtrace.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Coarse deployment-level MSPT evidence. Method-level attribution remains Spark's job. */
public final class MsptRegressionDetector {
    private MsptRegressionDetector() {}

    public static double median(List<Double> values) {
        if (values == null || values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
        List<Double> sorted = new ArrayList<>(values);
        sorted.sort(Double::compareTo);
        int middle = sorted.size() / 2;
        return sorted.size() % 2 == 1
                ? sorted.get(middle)
                : (sorted.get(middle - 1) + sorted.get(middle)) / 2.0;
    }

    public static boolean material(double baselineMedian, double currentMedian) {
        return baselineMedian > 0.0
                && currentMedian - baselineMedian >= 10.0
                && currentMedian >= baselineMedian * 1.5;
    }

    public static CheckResult check(Double baselineMedian, List<Double> currentSamples) {
        if (baselineMedian == null || baselineMedian <= 0.0 || currentSamples == null || currentSamples.isEmpty()) {
            return new CheckResult("mspt-regression", "Five-minute MSPT regression",
                    CheckStatus.UNKNOWN, CheckCriticality.WARNING,
                    "Not enough five-minute MSPT evidence for a baseline comparison", Map.of());
        }
        double currentMedian = median(currentSamples);
        Map<String, Object> details = Map.of(
                "baselineMedianMspt", baselineMedian,
                "currentMedianMspt", currentMedian,
                "sampleCount", currentSamples.size()
        );
        if (material(baselineMedian, currentMedian)) {
            return new CheckResult("mspt-regression", "Five-minute MSPT regression",
                    CheckStatus.WARN, CheckCriticality.WARNING,
                    "Median MSPT materially increased; capture a spark profile before assigning performance blame",
                    details);
        }
        return new CheckResult("mspt-regression", "Five-minute MSPT regression",
                CheckStatus.PASS, CheckCriticality.WARNING,
                "No material five-minute median MSPT regression detected", details);
    }
}
