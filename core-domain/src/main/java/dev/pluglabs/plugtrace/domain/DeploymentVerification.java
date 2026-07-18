package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record DeploymentVerification(
        String id,
        String deploymentId,
        Instant verifiedAt,
        DeploymentHealth health,
        List<CheckResult> checks,
        boolean observationWindowComplete,
        boolean newSevereIssue
) {
    public DeploymentVerification {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deploymentId, "deploymentId");
        Objects.requireNonNull(verifiedAt, "verifiedAt");
        Objects.requireNonNull(health, "health");
        checks = checks == null ? List.of() : List.copyOf(checks);
    }
}
