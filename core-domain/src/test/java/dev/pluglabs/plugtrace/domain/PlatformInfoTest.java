package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class PlatformInfoTest {
    @Test
    void paperModernOnFoliaWarnsToMigrate() {
        PlatformInfo info = PlatformInfo.detect("Folia", "1.21.4-Folia", "paper-modern");
        assertEquals("folia", info.forkFamily());
        assertEquals("paper-modern", info.artifact());
        assertNotNull(info.migrateHint());
        assertTrueContains(info.migrateHint(), "PlugTrace-folia");
    }

    @Test
    void foliaArtifactOnFoliaIsLabeledAsDogfoodUntilSoakCompletes() {
        PlatformInfo info = PlatformInfo.detect("Folia", "git-Folia", "folia");
        assertEquals("folia", info.artifact());
        assertEquals("Dogfood verified (Folia artifact; soak pending)", info.supportTier());
        assertNull(info.migrateHint());
    }

    @Test
    void spigotPrefersBukkitModernGuidance() {
        PlatformInfo info = PlatformInfo.detect("Spigot", "1.21.4-R0.1-SNAPSHOT", "paper-modern");
        assertEquals("bukkit-family", info.forkFamily());
        assertNotNull(info.migrateHint());
    }

    @Test
    void bukkitModernOnSpigotIsExperimental() {
        PlatformInfo info = PlatformInfo.detect("CraftBukkit", "Spigot", "bukkit-modern");
        assertEquals("bukkit-modern", info.artifact());
        assertEquals("Experimental Java 17 adapter", info.supportTier());
    }

    private static void assertTrueContains(String haystack, String needle) {
        if (haystack == null || !haystack.contains(needle)) {
            throw new AssertionError("Expected '" + haystack + "' to contain '" + needle + "'");
        }
    }
}
