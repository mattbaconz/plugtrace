package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeploymentLifecycleEvidenceTest {
    @Test
    void recordsReadyAndCleanShutdownWithoutLosingSnapshotEvidence() {
        Instant started = Instant.parse("2026-07-16T01:00:00Z");
        Instant ready = Instant.parse("2026-07-16T01:00:12Z");
        Instant stopped = Instant.parse("2026-07-16T02:00:00Z");
        Deployment deployment = Deployment.builder()
                .id("d1")
                .localSequence(1)
                .nodeId("n1")
                .startedAt(started)
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .complete(false)
                .tags(List.of("before-update"))
                .build();

        Deployment readyDeployment = deployment.withReadyEvidence(ready, 12_000L);
        Deployment stoppedDeployment = readyDeployment.withTermination(
                stopped, DeploymentLifecycle.STOPPED_CLEANLY, List.of());

        assertEquals(ready, stoppedDeployment.startupReadyAt());
        assertEquals(12_000L, stoppedDeployment.startupReadyMillis());
        assertEquals(stopped, stoppedDeployment.endedAt());
        assertEquals(DeploymentLifecycle.STOPPED_CLEANLY, stoppedDeployment.lifecycle());
        assertTrue(stoppedDeployment.complete());
        assertEquals(List.of("before-update"), stoppedDeployment.tags());
    }

    @Test
    void reconstructsAnUnfinishedPreviousBootFromCrashReports() {
        Deployment previous = Deployment.builder()
                .id("d1")
                .localSequence(1)
                .nodeId("n1")
                .startedAt(Instant.parse("2026-07-16T01:00:00Z"))
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .complete(false)
                .build();
        Instant detectedAt = Instant.parse("2026-07-16T01:05:00Z");

        Deployment reconstructed = DeploymentLifecycleReconstructor.reconstruct(
                previous, detectedAt, List.of("crash-reports/crash-2026-07-16_01.04.59-server.txt"));

        assertEquals(DeploymentLifecycle.CRASHED, reconstructed.lifecycle());
        assertEquals(detectedAt, reconstructed.endedAt());
        assertFalse(reconstructed.complete());
        assertEquals(List.of("crash-reports/crash-2026-07-16_01.04.59-server.txt"),
                reconstructed.crashReportReferences());
    }

    @Test
    void reconstructsAnUnfinishedBootAsIncompleteWhenNoCrashReportExists() {
        Deployment previous = Deployment.builder()
                .id("d1")
                .localSequence(1)
                .nodeId("n1")
                .lifecycle(DeploymentLifecycle.STARTING)
                .complete(false)
                .build();

        Deployment reconstructed = DeploymentLifecycleReconstructor.reconstruct(
                previous, Instant.parse("2026-07-16T01:05:00Z"), List.of());

        assertEquals(DeploymentLifecycle.INCOMPLETE, reconstructed.lifecycle());
        assertFalse(reconstructed.complete());
        assertNull(reconstructed.startupReadyAt());
    }
}
