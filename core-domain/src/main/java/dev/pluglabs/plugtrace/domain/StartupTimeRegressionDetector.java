package dev.pluglabs.plugtrace.domain;

public final class StartupTimeRegressionDetector {
    private StartupTimeRegressionDetector() {}

    public static boolean material(long baselineMillis, long currentMillis) {
        return baselineMillis > 0
                && currentMillis - baselineMillis >= 10_000L
                && currentMillis >= baselineMillis * 1.5;
    }
}
