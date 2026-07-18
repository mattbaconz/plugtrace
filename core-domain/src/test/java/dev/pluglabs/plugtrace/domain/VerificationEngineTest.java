package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationEngineTest {
    private final VerificationEngine engine = new VerificationEngine();

    @Test
    void criticalFailureMakesDeploymentFailing() {
        DeploymentVerification verification = engine.evaluate(
                "v1",
                "d1",
                Instant.parse("2026-07-15T00:00:00Z"),
                List.of(new CheckResult(
                        "server-ready",
                        "Server ready",
                        CheckStatus.FAIL,
                        CheckCriticality.CRITICAL,
                        "Server did not reach ready state",
                        java.util.Map.of()
                )),
                false
        );

        assertEquals(DeploymentHealth.FAILING, verification.health());
    }

    @Test
    void warningMakesDeploymentDegraded() {
        DeploymentVerification verification = engine.evaluate(
                "v2",
                "d1",
                Instant.parse("2026-07-15T00:00:00Z"),
                List.of(new CheckResult(
                        "startup-time",
                        "Startup time",
                        CheckStatus.WARN,
                        CheckCriticality.WARNING,
                        "Startup was slower",
                        java.util.Map.of("baselineMs", 1000, "currentMs", 2000)
                )),
                false
        );

        assertEquals(DeploymentHealth.DEGRADED, verification.health());
    }

    @Test
    void passingChecksRemainObservingUntilWindowCompletes() {
        List<CheckResult> checks = List.of(CheckResult.pass("server-ready", "Server ready"));

        assertEquals(DeploymentHealth.UNKNOWN, engine.evaluate(
                "v3", "d1", Instant.now(), checks, false).health());
        assertEquals(DeploymentHealth.HEALTHY, engine.evaluate(
                "v4", "d1", Instant.now(), checks, true).health());
    }

    @Test
    void newSevereIssuePreventsHealthyPromotion() {
        DeploymentVerification verification = engine.evaluate(
                "v5",
                "d1",
                Instant.now(),
                List.of(CheckResult.pass("server-ready", "Server ready")),
                true,
                true
        );

        assertEquals(DeploymentHealth.DEGRADED, verification.health());
    }
}
