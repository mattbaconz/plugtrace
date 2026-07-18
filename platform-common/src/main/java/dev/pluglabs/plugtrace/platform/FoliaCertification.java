package dev.pluglabs.plugtrace.platform;

/**
 * Minimum Folia safety checks for the {@code folia} artifact.
 * Passing these checks is necessary, but never sufficient, for a public
 * compatibility claim: live fixtures and soak evidence are separate gates.
 */
public final class FoliaCertification {
    public static final String TIER_LABEL = "Dogfood verified (soak pending)";

    private FoliaCertification() {
    }

    /** True when running artifact id is folia and Folia runtime is detected. */
    public static boolean claimsMatch(String artifactId, boolean foliaRuntime) {
        return "folia".equals(artifactId) && foliaRuntime;
    }

    /**
     * Store / DB work must never be scheduled on region/main sync paths.
     * Async worker ownership is required for Folia-safe dogfood.
     */
    public static boolean storeIoIsAsyncOnly(boolean storeWritesUseSchedulerAsync) {
        return storeWritesUseSchedulerAsync;
    }

    /** Delayed Bukkit API follow-ups must use GlobalRegionScheduler on Folia. */
    public static boolean delayedSyncUsesGlobalRegion(boolean folia, boolean usesGlobalRegionScheduler) {
        return !folia || usesGlobalRegionScheduler;
    }

    public static boolean suitePasses(boolean artifactIsFolia, boolean foliaRuntimeDetected,
                                      boolean asyncStoreIo, boolean globalRegionForDelayedSync) {
        return claimsMatch(artifactIsFolia ? "folia" : "other", foliaRuntimeDetected)
                && storeIoIsAsyncOnly(asyncStoreIo)
                && delayedSyncUsesGlobalRegion(foliaRuntimeDetected, globalRegionForDelayedSync);
    }
}
