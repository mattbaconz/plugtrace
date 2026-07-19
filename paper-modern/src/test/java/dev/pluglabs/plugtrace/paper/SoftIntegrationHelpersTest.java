package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SoftIntegrationHelpersTest {
    @Test
    void updaterSummaryNullWhenEmpty() {
        assertNull(UpdaterCoexistence.summary(List.of()));
        assertNull(UpdaterCoexistence.summary(null));
    }

    @Test
    void updaterSummaryListsNames() {
        String summary = UpdaterCoexistence.summary(List.of("AutoUpdatePlugins"));
        assertTrue(summary.contains("AutoUpdatePlugins"));
        assertTrue(summary.contains("verifies"));
    }
}
