package dev.pluglabs.plugtrace.platform;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Capability registry stub for adapters (Folia schedulers, Spark soft-link, etc.). */
public final class CapabilityRegistry {
    public enum Capability {
        FOLIA_SCHEDULERS,
        RICH_LIFECYCLE,
        SPARK_SOFT_LINK,
        DEPLOYMENT_SNAPSHOT,
        REPORT_HTML
    }

    private final EnumSet<Capability> capabilities;

    public CapabilityRegistry(Set<Capability> capabilities) {
        this.capabilities = capabilities == null || capabilities.isEmpty()
                ? EnumSet.noneOf(Capability.class)
                : EnumSet.copyOf(capabilities);
    }

    public boolean has(Capability capability) {
        return capabilities.contains(capability);
    }

    public Set<Capability> all() {
        return Collections.unmodifiableSet(capabilities);
    }

    public static CapabilityRegistry forArtifact(String artifactId) {
        EnumSet<Capability> set = EnumSet.of(
                Capability.SPARK_SOFT_LINK,
                Capability.DEPLOYMENT_SNAPSHOT,
                Capability.REPORT_HTML
        );
        if (!ArtifactIds.BUKKIT_MODERN.equals(artifactId)) {
            set.add(Capability.RICH_LIFECYCLE);
        }
        if (ArtifactIds.FOLIA.equals(artifactId)) {
            set.add(Capability.FOLIA_SCHEDULERS);
        }
        return new CapabilityRegistry(set);
    }
}
