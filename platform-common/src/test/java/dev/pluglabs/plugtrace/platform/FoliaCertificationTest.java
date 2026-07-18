package dev.pluglabs.plugtrace.platform;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FoliaCertificationTest {
    @Test
    void suitePassesOnlyForFoliaArtifactOnFoliaRuntime() {
        assertTrue(FoliaCertification.suitePasses(true, true, true, true));
        assertFalse(FoliaCertification.suitePasses(false, true, true, true));
        assertFalse(FoliaCertification.suitePasses(true, false, true, true));
        assertFalse(FoliaCertification.suitePasses(true, true, false, true));
        assertFalse(FoliaCertification.suitePasses(true, true, true, false));
    }

    @Test
    void paperModernNeverClaimsFoliaSupport() {
        assertFalse(FoliaCertification.claimsMatch("paper-modern", true));
        assertEquals("Dogfood verified (soak pending)", FoliaCertification.TIER_LABEL);
    }

    @Test
    void delayedSyncRuleAllowsNonFoliaWithoutGlobalRegion() {
        assertTrue(FoliaCertification.delayedSyncUsesGlobalRegion(false, false));
        assertTrue(FoliaCertification.delayedSyncUsesGlobalRegion(true, true));
        assertFalse(FoliaCertification.delayedSyncUsesGlobalRegion(true, false));
    }
}
