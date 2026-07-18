package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RegressionAndAttributionTest {
    @Test
    void classifiesNewIssueAgainstBaseline() {
        Issue baselineIssue = issue("fp-old", IssueStatus.ONGOING);
        Issue currentNew = issue("fp-new", IssueStatus.ONGOING);

        List<Issue> classified = new RegressionEngine().classify(
                List.of(currentNew),
                List.of(baselineIssue),
                List.of()
        );

        assertEquals(IssueStatus.NEW, classified.get(0).status());
        assertEquals(RegressionClass.NEW_ISSUE, classified.get(0).regressionClass());
    }

    @Test
    void attributionReturnsUnknownWithoutEvidence() {
        List<Suspect> suspects = new AttributionEngine().attribute(List.of(), List.of());
        assertEquals(ConfidenceBand.UNKNOWN, suspects.get(0).band());
    }

    @Test
    void attributionPrefersOwnershipPlusChange() {
        Change change = new Change(
                ChangeType.BINARY_CHANGED_SAME_VERSION,
                "PLUGIN:Shop",
                "aaa",
                "bbb",
                "Version remains 2.4.0, but binary hash changed.",
                90
        );
        Issue issue = new Issue(
                "i1",
                "fp1",
                "exception",
                "failed",
                List.of("frame:Shop"),
                Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-13T00:00:01Z"),
                IssueStatus.NEW,
                "error",
                1,
                "at com.shop.Main.enable",
                RegressionClass.NEW_ISSUE
        );

        List<Suspect> suspects = new AttributionEngine().attribute(List.of(issue), List.of(change));
        assertEquals("PLUGIN:shop", suspects.get(0).componentKey());
        assertTrue(suspects.get(0).band() == ConfidenceBand.MEDIUM
                || suspects.get(0).band() == ConfidenceBand.HIGH);
    }

    @Test
    void highConfidenceRequiresChangedComponentAndStrongRuntimeOwnership() {
        Change change = new Change(ChangeType.VERSION_CHANGED, "PLUGIN:Shop", "1", "2",
                "Shop changed", 70);
        Issue loggerOnly = issue("fp-logger", IssueStatus.NEW, 2, List.of("logger:Shop"));
        Issue frameOwned = issue("fp-frame", IssueStatus.NEW, 2,
                List.of("frame:Shop", "wrapper:ProtocolLib"));

        Suspect loggerSuspect = new AttributionEngine().attribute(List.of(loggerOnly), List.of(change)).get(0);
        List<Suspect> frameSuspects = new AttributionEngine().attribute(List.of(frameOwned), List.of(change));

        assertEquals(ConfidenceBand.MEDIUM, loggerSuspect.band());
        assertEquals(ConfidenceBand.HIGH, frameSuspects.get(0).band());
        assertTrue(frameSuspects.stream().noneMatch(s -> s.componentKey().equals("PLUGIN:protocollib")));
        assertTrue(frameSuspects.get(0).supporting().stream()
                .anyMatch(e -> e.explanation().contains("meaningful stack frame")));
    }

    @Test
    void classifiesReturnedWorsenedExistingAndResolvedIssues() {
        Issue returnedBefore = issue("returned", IssueStatus.RESOLVED, 1, List.of());
        Issue returnedNow = issue("returned", IssueStatus.ONGOING, 2, List.of());
        Issue worsenedBefore = issue("worsened", IssueStatus.ONGOING, 5, List.of());
        Issue worsenedNow = issue("worsened", IssueStatus.ONGOING, 30, List.of());
        Issue existingBefore = issue("existing", IssueStatus.ONGOING, 5, List.of());
        Issue existingNow = issue("existing", IssueStatus.ONGOING, 6, List.of());
        Issue resolvedBefore = issue("resolved", IssueStatus.ONGOING, 5, List.of());

        List<Issue> classified = new RegressionEngine().classify(
                List.of(returnedNow, worsenedNow, existingNow),
                List.of(returnedBefore, worsenedBefore, existingBefore, resolvedBefore), List.of());

        assertEquals(IssueStatus.RETURNED, find(classified, "returned").status());
        assertEquals(RegressionClass.RETURNED_ISSUE, find(classified, "returned").regressionClass());
        assertEquals(RegressionClass.WORSENED_ISSUE, find(classified, "worsened").regressionClass());
        assertEquals(IssueStatus.ONGOING, find(classified, "existing").status());
        assertEquals(IssueStatus.RESOLVED, find(classified, "resolved").status());
    }

    private static Issue issue(String fingerprint, IssueStatus status) {
        return issue(fingerprint, status, 1, List.of());
    }

    private static Issue issue(String fingerprint, IssueStatus status, long count, List<String> ownership) {
        return new Issue(
                fingerprint,
                fingerprint,
                "exception",
                "msg",
                ownership,
                Instant.parse("2026-07-13T00:00:00Z"),
                Instant.parse("2026-07-13T00:00:00Z"),
                status,
                "error",
                count,
                null,
                RegressionClass.NONE
        );
    }

    private static Issue find(List<Issue> issues, String fingerprint) {
        return issues.stream().filter(issue -> issue.fingerprint().equals(fingerprint)).findFirst().orElseThrow();
    }
}
