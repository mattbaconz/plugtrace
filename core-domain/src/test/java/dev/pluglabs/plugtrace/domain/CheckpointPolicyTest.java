package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckpointPolicyTest {
    @Test
    void onlyHealthyDeploymentsPassRequireHealthy() {
        assertDoesNotThrow(() -> CheckpointPolicy.requireHealthy(deployment(DeploymentHealth.HEALTHY)));
        assertThrows(IllegalStateException.class,
                () -> CheckpointPolicy.requireHealthy(deployment(DeploymentHealth.UNKNOWN)));
        assertThrows(IllegalStateException.class,
                () -> CheckpointPolicy.requireHealthy(deployment(DeploymentHealth.DEGRADED)));
    }

    @Test
    void decideAllowsHealthyPromotesUnknownRejectsBroken() {
        assertEquals(CheckpointPolicy.Decision.ALLOW, CheckpointPolicy.decide(DeploymentHealth.HEALTHY));
        assertEquals(CheckpointPolicy.Decision.PROMOTE_UNKNOWN, CheckpointPolicy.decide(DeploymentHealth.UNKNOWN));
        assertEquals(CheckpointPolicy.Decision.REJECT, CheckpointPolicy.decide(DeploymentHealth.FAILING));
        assertEquals(CheckpointPolicy.Decision.REJECT, CheckpointPolicy.decide(DeploymentHealth.DEGRADED));
        assertEquals(CheckpointPolicy.Decision.REJECT, CheckpointPolicy.decide(DeploymentHealth.CRASHED));
    }

    @Test
    void requireNotBrokenAllowsHealthyAndUnknown() {
        assertDoesNotThrow(() -> CheckpointPolicy.requireNotBroken(deployment(DeploymentHealth.HEALTHY)));
        assertDoesNotThrow(() -> CheckpointPolicy.requireNotBroken(deployment(DeploymentHealth.UNKNOWN)));
        assertThrows(IllegalStateException.class,
                () -> CheckpointPolicy.requireNotBroken(deployment(DeploymentHealth.FAILING)));
    }

    private Deployment deployment(DeploymentHealth health) {
        return Deployment.builder()
                .id("deployment")
                .nodeId("node")
                .localSequence(1)
                .startedAt(Instant.parse("2026-07-16T00:00:00Z"))
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(health)
                .build();
    }
}
