package dev.pluglabs.plugtrace.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CapabilityRegistryTest {
    @Test
    void foliaArtifactIncludesFoliaSchedulers() {
        CapabilityRegistry caps = CapabilityRegistry.forArtifact(ArtifactIds.FOLIA);
        assertTrue(caps.has(CapabilityRegistry.Capability.FOLIA_SCHEDULERS));
        assertTrue(caps.has(CapabilityRegistry.Capability.SPARK_SOFT_LINK));
    }

    @Test
    void paperModernDoesNotClaimFoliaSchedulers() {
        CapabilityRegistry caps = CapabilityRegistry.forArtifact(ArtifactIds.PAPER_MODERN);
        assertFalse(caps.has(CapabilityRegistry.Capability.FOLIA_SCHEDULERS));
    }

    @Test
    void bukkitArtifactDoesNotClaimRichLifecycle() {
        CapabilityRegistry caps = CapabilityRegistry.forArtifact(ArtifactIds.BUKKIT_MODERN);
        assertFalse(caps.has(CapabilityRegistry.Capability.RICH_LIFECYCLE));
    }

    @Test
    void detectFoliaFromServerName() {
        assertTrue(FoliaDetect.isFolia("Folia", "1.21.4"));
        assertFalse(FoliaDetect.isFolia("Paper", "1.21.4-R0.1-SNAPSHOT"));
    }
}
