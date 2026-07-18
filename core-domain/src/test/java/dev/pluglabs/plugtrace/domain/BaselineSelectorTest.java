package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BaselineSelectorTest {
    @Test
    void refusesAnUnknownDeploymentAsAHealthyBaseline() {
        Deployment unknown = deployment(1, DeploymentHealth.UNKNOWN);
        Deployment current = deployment(2, DeploymentHealth.UNKNOWN);

        assertTrue(new BaselineSelector().select(List.of(current, unknown), current).isEmpty());
    }

    @Test
    void selectsNewestHealthyDeploymentEvenWhenANewerUnknownDeploymentExists() {
        Deployment healthy = deployment(1, DeploymentHealth.HEALTHY);
        Deployment unknown = deployment(2, DeploymentHealth.UNKNOWN);
        Deployment current = deployment(3, DeploymentHealth.UNKNOWN);

        assertEquals(healthy, new BaselineSelector().select(List.of(current, unknown, healthy), current).orElseThrow());
    }

    private Deployment deployment(long sequence, DeploymentHealth health) {
        return Deployment.builder()
                .id("deployment-" + sequence)
                .nodeId("test-node")
                .localSequence(sequence)
                .startedAt(Instant.parse("2026-07-16T00:00:00Z").plusSeconds(sequence))
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(health)
                .build();
    }
}
