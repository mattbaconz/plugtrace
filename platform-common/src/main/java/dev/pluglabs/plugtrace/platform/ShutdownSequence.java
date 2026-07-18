package dev.pluglabs.plugtrace.platform;

/** Ensures asynchronous producers are stopped before their persistence consumers close. */
public final class ShutdownSequence {
    private ShutdownSequence() {}

    public static void close(AutoCloseable asyncProducers, AutoCloseable persistentService) {
        RuntimeException failure = null;
        try {
            if (asyncProducers != null) asyncProducers.close();
        } catch (Exception e) {
            failure = new IllegalStateException("Failed to stop async PlugTrace producers", e);
        }
        try {
            if (persistentService != null) persistentService.close();
        } catch (Exception e) {
            if (failure == null) failure = new IllegalStateException("Failed to close PlugTrace service", e);
            else failure.addSuppressed(e);
        }
        if (failure != null) throw failure;
    }
}
