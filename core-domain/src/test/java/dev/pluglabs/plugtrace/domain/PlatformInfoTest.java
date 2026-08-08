package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlatformInfoTest {
    @Test
    void paperModernOnFoliaIsUnifiedJar() {
        PlatformInfo info = PlatformInfo.detect("Folia", "1.21.4-Folia", "paper-modern");
        assertEquals("folia", info.forkFamily());
        assertEquals("paper-modern", info.artifact());
        assertNull(info.migrateHint());
        assertEquals("Dogfood verified (Folia-capable jar; soak cleared)", info.supportTier());
    }

    @Test
    void foliaArtifactOnFoliaIsLabeledAsDogfood() {
        PlatformInfo info = PlatformInfo.detect("Folia", "git-Folia", "folia");
        assertEquals("folia", info.artifact());
        assertEquals("Dogfood verified (Folia-capable jar; soak cleared)", info.supportTier());
        assertNull(info.migrateHint());
    }

    @Test
    void paperModernOnSpigotIsExperimentalUnified() {
        PlatformInfo info = PlatformInfo.detect("Spigot", "1.20.4-R0.1-SNAPSHOT", "paper-modern");
        assertEquals("bukkit-family", info.forkFamily());
        assertNull(info.migrateHint());
        assertEquals("Experimental dogfood (Java 17 / api 1.20)", info.supportTier());
    }

    @Test
    void bukkitModernOnSpigotIsExperimental() {
        PlatformInfo info = PlatformInfo.detect("CraftBukkit", "Spigot", "bukkit-modern");
        assertEquals("bukkit-modern", info.artifact());
        assertEquals("Experimental dogfood (Java 17 / api 1.20)", info.supportTier());
    }
}
