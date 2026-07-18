package dev.pluglabs.plugtrace.domain;

public final class ConfigResetDetector {
    private ConfigResetDetector() { }

    public static boolean possibleReset(ConfigSnapshot before, ConfigSnapshot after) {
        if (before == null || after == null
                || before.sizeBytes() <= 0 || before.structuralKeyCount() <= 0
                || after.sizeBytes() < 0 || after.structuralKeyCount() < 0) {
            return false;
        }
        double sizeLoss = 1d - ((double) after.sizeBytes() / before.sizeBytes());
        double keyLoss = 1d - ((double) after.structuralKeyCount() / before.structuralKeyCount());
        return sizeLoss >= 0.40d && keyLoss >= 0.40d;
    }
}
