package dev.pluglabs.plugtrace.domain;

import java.util.Locale;
import java.util.Objects;

/** Detected server fork / support tier labeling for the running PlugTrace artifact. */
public final class PlatformInfo {
    private final String forkFamily;
    private final String supportTier;
    private final String artifact;
    private final String migrateHint;

    public PlatformInfo(String forkFamily, String supportTier, String artifact) {
        this(forkFamily, supportTier, artifact, null);
    }

    public PlatformInfo(String forkFamily, String supportTier, String artifact, String migrateHint) {
        this.forkFamily = forkFamily == null ? "unknown" : forkFamily;
        this.supportTier = supportTier == null ? "unknown" : supportTier;
        this.artifact = artifact == null ? "paper-modern" : artifact;
        this.migrateHint = migrateHint;
    }

    public String forkFamily() {
        return forkFamily;
    }

    public String supportTier() {
        return supportTier;
    }

    public String artifact() {
        return artifact;
    }

    public String migrateHint() {
        return migrateHint;
    }

    public static PlatformInfo detect(String serverName, String versionString) {
        return detect(serverName, versionString, "paper-modern");
    }

    public static PlatformInfo detect(String serverName, String versionString, String runningArtifact) {
        String artifact = runningArtifact == null || runningArtifact.isBlank() ? "paper-modern" : runningArtifact;
        String haystack = ((serverName == null ? "" : serverName) + " " + (versionString == null ? "" : versionString))
                .toLowerCase(Locale.ROOT);

        if (haystack.contains("folia")) {
            if ("folia".equals(artifact) || "paper-modern".equals(artifact)) {
                return new PlatformInfo("folia", "Dogfood verified (Folia-capable jar; soak cleared)", artifact);
            }
            return new PlatformInfo(
                    "folia",
                    "Experimental (no public Folia claim)",
                    artifact,
                    "Folia detected — use PlugTrace-1.0.0.jar (folia-supported), not " + artifact
            );
        }
        if (haystack.contains("purpur")) {
            return new PlatformInfo("purpur", "Dogfood verified (soak pending)", artifact);
        }
        if (haystack.contains("pufferfish")) {
            return new PlatformInfo("pufferfish", "Unverified", artifact);
        }
        if (haystack.contains("paper")) {
            String tier = "paper-modern".equals(artifact) ? "Dogfood verified (soak pending)" : "Experimental";
            return new PlatformInfo("paper", tier, artifact);
        }
        if (haystack.contains("spigot") || haystack.contains("craftbukkit") || haystack.contains("bukkit")) {
            if ("bukkit-modern".equals(artifact) || "paper-modern".equals(artifact)) {
                return new PlatformInfo("bukkit-family", "Experimental dogfood (Java 17 / api 1.20)", artifact);
            }
            return new PlatformInfo(
                    "bukkit-family",
                    "Compatible (unverified)",
                    artifact,
                    "Spigot/Bukkit detected — use PlugTrace-1.0.0.jar (api-version 1.20)"
            );
        }
        return new PlatformInfo("unknown", "Unknown", artifact);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PlatformInfo that)) {
            return false;
        }
        return Objects.equals(forkFamily, that.forkFamily)
                && Objects.equals(supportTier, that.supportTier)
                && Objects.equals(artifact, that.artifact)
                && Objects.equals(migrateHint, that.migrateHint);
    }

    @Override
    public int hashCode() {
        return Objects.hash(forkFamily, supportTier, artifact, migrateHint);
    }
}
