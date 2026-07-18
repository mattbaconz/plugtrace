package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/** Creates runtime incidents only from newly introduced severe evidence. */
public final class IncidentEngine {
    public Optional<Incident> openForRuntimeIssues(
            String deploymentId, Instant openedAt, List<Issue> issues, List<Incident> existing) {
        Set<String> alreadyAttached = existing == null ? Set.of() : existing.stream()
                .filter(incident -> incident.status() == IncidentStatus.OPEN)
                .flatMap(incident -> incident.issueFingerprints().stream())
                .collect(Collectors.toSet());
        List<String> severe = issues == null ? List.of() : issues.stream()
                .filter(this::isNewSevere)
                .map(Issue::fingerprint)
                .filter(fingerprint -> !alreadyAttached.contains(fingerprint))
                .distinct()
                .toList();
        if (severe.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new Incident(
                UUID.randomUUID().toString(), deploymentId, null, openedAt, null, IncidentStatus.OPEN,
                "New severe runtime issue detected", severe, List.of("new-severe-issues")));
    }

    private boolean isNewSevere(Issue issue) {
        String severity = issue.severity().toLowerCase(Locale.ROOT);
        boolean severe = severity.contains("error") || severity.contains("severe");
        boolean introduced = issue.status() == IssueStatus.NEW
                || issue.status() == IssueStatus.RETURNED
                || issue.regressionClass() == RegressionClass.NEW_ISSUE
                || issue.regressionClass() == RegressionClass.RETURNED_ISSUE
                || issue.regressionClass() == RegressionClass.STARTUP_REGRESSION;
        return severe && introduced;
    }
}
