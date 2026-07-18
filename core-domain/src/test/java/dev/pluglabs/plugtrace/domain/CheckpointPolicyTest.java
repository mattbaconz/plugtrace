package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CheckpointPolicyTest {
    @Test
    void onlyHealthyDeploymentsCanBecomeCheckpoints() {
        assertDoesNotThrow(() -> CheckpointPolicy.requireHealthy(deployment(DeploymentHealth.HEALTHY)));
        assertThrows(IllegalStateException.class,
                () -> CheckpointPolicy.requireHealthy(deployment(DeploymentHealth.UNKNOWN)));
        assertThrows(IllegalStateException.class,
                () -> CheckpointPolicy.requireHealthy(deployment(DeploymentHealth.DEGRADED)));
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
