package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NoiseRulesAndPlatformTest {
    @Test
    void noiseRulesMarkExpected() {
        Issue issue = new Issue(
                "i1", "fp-noise", "exception", "benign warning",
                List.of(), Instant.now(), Instant.now(),
                IssueStatus.ONGOING, "warning", 1, null, RegressionClass.NONE
        );
        NoiseRules rules = NoiseRules.fromLists(List.of("fp-noise"), List.of());
        List<Issue> classified = new RegressionEngine().classify(List.of(issue), List.of(), List.of(), rules);
        assertEquals(IssueStatus.EXPECTED, classified.get(0).status());
    }

    @Test
    void detectsPurpurAndPaper() {
        assertEquals("purpur", PlatformInfo.detect("Purpur", "git-Purpur-123").forkFamily());
        assertEquals("Dogfood verified (soak pending)", PlatformInfo.detect("Purpur", "git-Purpur-123").supportTier());
        assertEquals("paper", PlatformInfo.detect("Paper", "git-Paper-123").forkFamily());
        assertTrue(PlatformInfo.detect("Paper", "git-Paper-123").supportTier().contains("Dogfood"));
    }
}
