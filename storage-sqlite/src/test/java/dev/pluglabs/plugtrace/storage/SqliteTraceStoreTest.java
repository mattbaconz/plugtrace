package dev.pluglabs.plugtrace.storage;

import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.ComponentIdentity;
import dev.pluglabs.plugtrace.domain.ComponentSnapshot;
import dev.pluglabs.plugtrace.domain.ComponentType;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycle;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.IssueStatus;
import dev.pluglabs.plugtrace.domain.RegressionClass;
import dev.pluglabs.plugtrace.domain.ServerNode;
import dev.pluglabs.plugtrace.domain.Checkpoint;
import dev.pluglabs.plugtrace.domain.CheckResult;
import dev.pluglabs.plugtrace.domain.DeploymentVerification;
import dev.pluglabs.plugtrace.domain.Incident;
import dev.pluglabs.plugtrace.domain.IncidentStatus;
import dev.pluglabs.plugtrace.domain.ExpectedState;
import dev.pluglabs.plugtrace.domain.RecoveryOutcome;
import dev.pluglabs.plugtrace.domain.RecoveryVerification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SqliteTraceStoreTest {
    @TempDir
    Path tempDir;

    private SqliteTraceStore store;

    @BeforeEach
    void setUp() {
        store = new SqliteTraceStore(tempDir.resolve("test.db"));
    }

    @AfterEach
    void tearDown() {
        store.close();
    }

    @Test
    void persistsDeploymentAndIssues() {
        store.upsertNode(new ServerNode("n1", "test", "paper", "test", Instant.now()));
        Deployment deployment = Deployment.builder()
                .id("d1")
                .localSequence(1)
                .nodeId("n1")
                .stateFingerprint("abc")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.HEALTHY)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVersion("21")
                .javaVendor("Temurin")
                .startupReadyAt(Instant.parse("2026-07-16T01:00:12Z"))
                .startupReadyMillis(12_000L)
                .crashReportReferences(List.of("crash-reports/example.txt"))
                .components(List.of(new ComponentSnapshot(
                        new ComponentIdentity(
                                ComponentType.PLUGIN, "Demo", "1.0", "hash",
                                List.of(), List.of(), List.of(), "Main", "1.21"
                        ),
                        "plugins/Demo.jar", 10, true, true, null
                )))
                .build();
        store.saveDeployment(deployment);
        store.saveIssue("d1", new Issue(
                "i1", "fp1", "exception", "boom", List.of("Demo"),
                Instant.now(), Instant.now(), IssueStatus.NEW, "error", 1, "stack",
                RegressionClass.NEW_ISSUE
        ));

        Deployment persisted = store.findDeployment("d1").orElseThrow();
        assertEquals(Instant.parse("2026-07-16T01:00:12Z"), persisted.startupReadyAt());
        assertEquals(12_000L, persisted.startupReadyMillis());
        assertEquals(List.of("crash-reports/example.txt"), persisted.crashReportReferences());
        assertEquals(1, store.listIssues("d1").size());
        assertEquals(2, store.nextSequence("n1"));
        assertEquals("ok", store.integrityCheck().toLowerCase());

        store.saveAnnotation(new Annotation(
                "a1", "d1", Instant.now(), "console", "ops", "maintenance window", null
        ));
        assertEquals(1, store.listAnnotations("d1").size());

        for (int i = 2; i <= 5; i++) {
            store.saveDeployment(Deployment.builder()
                    .id("d" + i)
                    .localSequence(i)
                    .nodeId("n1")
                    .stateFingerprint("fp" + i)
                    .lifecycle(DeploymentLifecycle.OBSERVING)
                    .health(DeploymentHealth.UNKNOWN)
                    .serverImplementation("Paper")
                    .minecraftVersion("1.21.4")
                    .javaVersion("21")
                    .javaVendor("Temurin")
                    .build());
        }
        int removed = store.pruneDeployments("n1", 3, "d5");
        assertTrue(removed >= 1);
        assertTrue(store.countDeployments("n1") <= 4);
        assertTrue(store.findDeployment("d5").isPresent());
    }

    @Test
    void persistsV3CheckpointVerificationAndIncident() {
        store.upsertNode(new ServerNode("n1", "test", "paper", "test", Instant.now()));
        Deployment deployment = Deployment.builder()
                .id("d1").localSequence(1).nodeId("n1")
                .health(DeploymentHealth.UNKNOWN)
                .serverImplementation("Paper").minecraftVersion("1.21.11")
                .javaVersion("21").javaVendor("Temurin").build();
        store.saveDeployment(deployment);

        Checkpoint checkpoint = new Checkpoint("c1", "d1", "before update", Instant.now(), "console");
        store.saveCheckpoint(checkpoint);
        assertEquals("c1", store.findCheckpoint("c1").orElseThrow().id());

        DeploymentVerification verification = new DeploymentVerification(
                "v1", "d1", Instant.now(), DeploymentHealth.DEGRADED,
                List.of(CheckResult.pass("server-ready", "Server ready")), false, true);
        store.saveVerification(verification);
        assertEquals("v1", store.findLatestVerification("d1").orElseThrow().id());

        Incident incident = new Incident(
                "incident-1", "d1", "v1", Instant.now(), null, IncidentStatus.OPEN,
                "Deployment verification degraded", List.of("fp1"), List.of("server-ready"));
        store.saveIncident(incident);
        assertEquals(1, store.listIncidents("d1", 10).size());

        ExpectedState expected = new ExpectedState("e1", "n1", "d1", Instant.now(),
                List.of("LuckPerms"), List.of("spawn"), List.of("world"), List.of("Economy"));
        store.saveExpectedState(expected);
        assertEquals(List.of("spawn"), store.findExpectedState("n1").orElseThrow().commands());

        RecoveryVerification recovery = new RecoveryVerification("r1", "d1", "plan1", Instant.now(),
                RecoveryOutcome.IMPROVED, 20.0, 2.0, List.of("economy-service: FAIL -> PASS"), "Improved");
        store.saveRecoveryVerification(recovery);
        assertEquals(RecoveryOutcome.IMPROVED,
                store.listRecoveryVerifications("d1").get(0).outcome());
    }
}
