package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecoveryVerificationEngineTest {
    @Test
    void comparesBeforeAndAfterCheckStatesAndIssueRates() {
        DeploymentVerification before = verification("before", CheckStatus.FAIL);
        DeploymentVerification after = verification("after", CheckStatus.PASS);

        RecoveryVerification result = new RecoveryVerificationEngine().evaluate(
                "r1", "after-deployment", "plan-1", Instant.parse("2026-07-16T03:00:00Z"),
                20.0, 0.0, before, after);

        assertEquals(RecoveryOutcome.RESOLVED, result.outcome());
        assertEquals(List.of("economy-service: FAIL -> PASS"), result.changedChecks());
    }

    @Test
    void remainsInsufficientWhenThereWasNoBeforeFailureEvidence() {
        RecoveryVerification result = new RecoveryVerificationEngine().evaluate(
                "r1", "after-deployment", "plan-1", Instant.now(),
                0.0, 0.0, verification("before", CheckStatus.PASS), verification("after", CheckStatus.PASS));

        assertEquals(RecoveryOutcome.INSUFFICIENT_EVIDENCE, result.outcome());
    }

    private static DeploymentVerification verification(String id, CheckStatus status) {
        CheckResult check = new CheckResult("economy-service", "Economy service", status,
                CheckCriticality.CRITICAL, status.name(), java.util.Map.of());
        return new DeploymentVerification(id, id, Instant.now(),
                status == CheckStatus.FAIL ? DeploymentHealth.FAILING : DeploymentHealth.HEALTHY,
                List.of(check), true, false);
    }
}
