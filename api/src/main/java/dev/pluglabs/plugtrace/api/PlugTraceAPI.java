package dev.pluglabs.plugtrace.api;

import java.util.Map;

/**
 * Public API for third-party plugins. Compile against this jar; call only when PlugTrace is installed.
 * Without PlugTrace, {@link #isAvailable()} is false and methods no-op.
 */
public final class PlugTraceAPI {
    private static volatile PlugTraceBridge bridge = PlugTraceBridge.NOOP;

    private PlugTraceAPI() {
    }

    /** Internal — called by PlugTrace paper module on enable/disable. */
    public static void bind(PlugTraceBridge implementation) {
        bridge = implementation == null ? PlugTraceBridge.NOOP : implementation;
    }

    public static boolean isAvailable() {
        return bridge.isAvailable();
    }

    public static void annotate(String category, String text) {
        bridge.annotate(category, text);
    }

    /**
     * Records a safe diagnostic field for the calling plugin.
     * Only string/number/boolean values; secret-like keys are redacted.
     */
    public static void recordSafeField(String pluginName, String key, Object value) {
        bridge.recordSafeField(pluginName, key, value);
    }

    /**
     * Attaches external release identity (e.g. PlugDev git commit / artifact hash) to the current deployment.
     * Map keys match plugdev-identity.json (schemaVersion, gitCommit, artifactHash, …).
     */
    public static void recordReleaseIdentity(Map<String, ?> identity) {
        bridge.recordReleaseIdentity(identity);
    }

    public static VerificationRegistration registerVerificationCheck(
            VerificationCheckDefinition definition,
            VerificationCheck check
    ) {
        return bridge.registerVerificationCheck(definition, check);
    }

    public static void recordMigration(MigrationRecord migration) {
        bridge.recordMigration(migration);
    }
}
