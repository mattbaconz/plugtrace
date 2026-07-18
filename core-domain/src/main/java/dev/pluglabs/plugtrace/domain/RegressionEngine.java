package dev.pluglabs.plugtrace.domain;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashMap;
import java.util.Map;

public final class RegressionEngine {
    public List<Issue> classify(
            List<Issue> currentIssues,
            List<Issue> baselineIssues,
            List<Change> changes
    ) {
        return classify(currentIssues, baselineIssues, changes, NoiseRules.empty());
    }

    public List<Issue> classify(
            List<Issue> currentIssues,
            List<Issue> baselineIssues,
            List<Change> changes,
            NoiseRules noiseRules
    ) {
        NoiseRules rules = noiseRules == null ? NoiseRules.empty() : noiseRules;
        Set<String> baselineFingerprints = new HashSet<>();
        Map<String, Issue> baselineByFingerprint = new LinkedHashMap<>();
        if (baselineIssues != null) {
            for (Issue issue : baselineIssues) {
                baselineFingerprints.add(issue.fingerprint());
                baselineByFingerprint.put(issue.fingerprint(), issue);
            }
        }

        boolean startupChange = changes.stream().anyMatch(c ->
                c.type() == ChangeType.LOAD_OUTCOME_CHANGED
                        || c.type() == ChangeType.COMPONENT_ADDED
                        || c.type() == ChangeType.COMPONENT_REMOVED
                        || c.type() == ChangeType.BINARY_CHANGED_SAME_VERSION
                        || c.type() == ChangeType.VERSION_CHANGED
        );

        List<Issue> classified = new ArrayList<>();
        for (Issue issue : currentIssues) {
            if (rules.isSuppressed(issue)) {
                classified.add(issue.withStatus(IssueStatus.EXPECTED, RegressionClass.NONE));
                continue;
            }
            Issue baselineIssue = baselineByFingerprint.get(issue.fingerprint());
            if (baselineIssue == null) {
                RegressionClass classification = isStartupRelated(issue) && startupChange
                        ? RegressionClass.STARTUP_REGRESSION
                        : RegressionClass.NEW_ISSUE;
                classified.add(issue.withStatus(IssueStatus.NEW, classification));
            } else if (baselineIssue.status() == IssueStatus.RESOLVED) {
                classified.add(issue.withStatus(IssueStatus.RETURNED, RegressionClass.RETURNED_ISSUE));
            } else if (materiallyWorsened(baselineIssue, issue)) {
                classified.add(issue.withStatus(IssueStatus.ONGOING, RegressionClass.WORSENED_ISSUE));
            } else {
                classified.add(issue.withStatus(IssueStatus.ONGOING, RegressionClass.NONE));
            }
        }
        Set<String> currentFingerprints = currentIssues.stream().map(Issue::fingerprint).collect(java.util.stream.Collectors.toSet());
        baselineByFingerprint.values().stream()
                .filter(issue -> !currentFingerprints.contains(issue.fingerprint()))
                .map(issue -> issue.withStatus(IssueStatus.RESOLVED, RegressionClass.NONE))
                .forEach(classified::add);
        return classified;
    }

    private static boolean materiallyWorsened(Issue baseline, Issue current) {
        return current.occurrenceCount() >= baseline.occurrenceCount() * 2
                && current.occurrenceCount() - baseline.occurrenceCount() >= 10;
    }

    private static boolean isStartupRelated(Issue issue) {
        String message = (issue.normalizedMessage() + " " + Objects.toString(issue.sampleStack(), ""))
                .toLowerCase(Locale.ROOT);
        return message.contains("enable")
                || message.contains("onenable")
                || message.contains("dependency")
                || message.contains("could not load")
                || message.contains("failed to load")
                || "startup".equalsIgnoreCase(issue.normalizedType());
    }
}
