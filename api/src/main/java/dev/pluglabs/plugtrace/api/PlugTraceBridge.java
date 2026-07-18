package dev.pluglabs.plugtrace.api;

import java.util.Map;

/** Implementation SPI registered by the PlugTrace plugin. */
public interface PlugTraceBridge {
    PlugTraceBridge NOOP = new PlugTraceBridge() {
        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void annotate(String category, String text) {
        }

        @Override
        public void recordSafeField(String pluginName, String key, Object value) {
        }

        @Override
        public void recordReleaseIdentity(Map<String, ?> identity) {
        }
    };

    boolean isAvailable();

    void annotate(String category, String text);

    void recordSafeField(String pluginName, String key, Object value);

    /** Records PlugDev (or other) external release identity for the current deployment. */
    void recordReleaseIdentity(Map<String, ?> identity);

    default VerificationRegistration registerVerificationCheck(
            VerificationCheckDefinition definition,
            VerificationCheck check
    ) {
        return VerificationRegistration.NOOP;
    }

    default void recordMigration(MigrationRecord migration) {
    }
}
