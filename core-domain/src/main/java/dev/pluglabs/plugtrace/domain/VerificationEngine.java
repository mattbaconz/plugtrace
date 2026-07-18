package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;

public final class VerificationEngine {
    public DeploymentVerification evaluate(
            String id,
            String deploymentId,
            Instant verifiedAt,
            List<CheckResult> checks,
            boolean observationWindowComplete
    ) {
        return evaluate(id, deploymentId, verifiedAt, checks, observationWindowComplete, false);
    }

    public DeploymentVerification evaluate(
            String id,
            String deploymentId,
            Instant verifiedAt,
            List<CheckResult> checks,
            boolean observationWindowComplete,
            boolean newSevereIssue
    ) {
        List<CheckResult> safeChecks = checks == null ? List.of() : List.copyOf(checks);
        boolean criticalFailure = safeChecks.stream().anyMatch(result ->
                result.status() == CheckStatus.FAIL && result.criticality() == CheckCriticality.CRITICAL);
        boolean degraded = newSevereIssue || safeChecks.stream().anyMatch(result ->
                result.status() == CheckStatus.WARN
                        || (result.status() == CheckStatus.FAIL
                        && result.criticality() != CheckCriticality.CRITICAL));

        DeploymentHealth health;
        if (criticalFailure) {
            health = DeploymentHealth.FAILING;
        } else if (degraded) {
            health = DeploymentHealth.DEGRADED;
        } else if (observationWindowComplete) {
            health = DeploymentHealth.HEALTHY;
        } else {
            health = DeploymentHealth.UNKNOWN;
        }
        return new DeploymentVerification(
                id,
                deploymentId,
                verifiedAt,
                health,
                safeChecks,
                observationWindowComplete,
                newSevereIssue
        );
    }
}
