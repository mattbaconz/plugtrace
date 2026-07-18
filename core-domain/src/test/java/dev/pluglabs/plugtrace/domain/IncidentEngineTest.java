package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IncidentEngineTest {
    @Test
    void opensOneRuntimeIncidentForNewSevereIssuesAndDeduplicatesExistingEvidence() {
        Issue severe = issue("fp-severe", IssueStatus.NEW, "error", RegressionClass.NEW_ISSUE);
        Issue warning = issue("fp-warning", IssueStatus.NEW, "warning", RegressionClass.NEW_ISSUE);

        Incident opened = new IncidentEngine().openForRuntimeIssues(
                "d1", Instant.parse("2026-07-16T02:00:00Z"), List.of(severe, warning), List.of())
                .orElseThrow();

        assertEquals(List.of("fp-severe"), opened.issueFingerprints());
        assertTrue(opened.summary().contains("runtime"));
        assertTrue(new IncidentEngine().openForRuntimeIssues(
                "d1", Instant.parse("2026-07-16T02:01:00Z"), List.of(severe), List.of(opened)).isEmpty());
    }

    private static Issue issue(String fingerprint, IssueStatus status, String severity, RegressionClass regression) {
        Instant now = Instant.parse("2026-07-16T02:00:00Z");
        return new Issue(fingerprint, fingerprint, "exception", "boom", List.of("frame:Shop"),
                now, now, status, severity, 1, "stack", regression);
    }
}
