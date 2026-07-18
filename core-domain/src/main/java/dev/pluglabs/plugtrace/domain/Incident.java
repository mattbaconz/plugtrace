package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record Incident(
        String id,
        String deploymentId,
        String verificationId,
        Instant openedAt,
        Instant resolvedAt,
        IncidentStatus status,
        String summary,
        List<String> issueFingerprints,
        List<String> failedCheckIds
) {
    public Incident {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(openedAt, "openedAt");
        Objects.requireNonNull(status, "status");
        summary = summary == null ? "" : summary;
        issueFingerprints = issueFingerprints == null ? List.of() : List.copyOf(issueFingerprints);
        failedCheckIds = failedCheckIds == null ? List.of() : List.copyOf(failedCheckIds);
    }
}
