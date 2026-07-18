package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Controlled proof-chain test without a live Paper server:
 * change fixture binary → new deployment → typed diff → new fingerprint → report suspects.
 */
class ProofChainTest {
    @Test
    void identifiesSameVersionBinaryChangeAsSuspect() {
        Deployment healthy = deployment(1, "hash-a", DeploymentHealth.HEALTHY);
        Deployment broken = deployment(2, "hash-b", DeploymentHealth.UNKNOWN);

        DiffEngine diffEngine = new DiffEngine();
        List<Change> changes = diffEngine.diff(healthy, broken);
        assertTrue(changes.stream().anyMatch(c -> c.type() == ChangeType.BINARY_CHANGED_SAME_VERSION));

        FingerprintEngine fingerprints = new FingerprintEngine();
        IssueEvent event = new IssueEvent(
                null,
                Instant.now(),
                broken.id(),
                "logger",
                "error",
                "java.lang.RuntimeException",
                "enable failed",
                "at com.fixture.SameVersionPlugin.onEnable(SameVersionPlugin.java:10)",
                List.of("PlugTraceFixtureSameVersion"),
                "Server thread"
        );
        String fp = fingerprints.fingerprint(event);
        Issue issue = new Issue(
                "i1", fp, "exception", fingerprints.normalize(event.message()),
                event.ownershipHints(), Instant.now(), Instant.now(),
                IssueStatus.ONGOING, "error", 1, event.stackTrace(), RegressionClass.NONE
        );

        List<Issue> classified = new RegressionEngine().classify(List.of(issue), List.of(), changes);
        assertEquals(RegressionClass.STARTUP_REGRESSION, classified.get(0).regressionClass());

        List<Suspect> suspects = new AttributionEngine().attribute(classified, changes);
        assertTrue(suspects.stream().anyMatch(s ->
                s.componentKey().equals("PLUGIN:plugtracefixturesameversion")
        ));
        assertTrue(suspects.get(0).band() == ConfidenceBand.MEDIUM
                || suspects.get(0).band() == ConfidenceBand.HIGH
                || suspects.get(0).band() == ConfidenceBand.LOW);

        // Ambiguous case remains unknown when no ownership/change link exists.
        Issue orphan = new Issue(
                "i2", "fp-orphan", "exception", "unrelated",
                List.of(), Instant.now(), Instant.now(),
                IssueStatus.NEW, "error", 1, null, RegressionClass.NEW_ISSUE
        );
        List<Suspect> unknown = new AttributionEngine().attribute(List.of(orphan), List.of());
        assertEquals(ConfidenceBand.UNKNOWN, unknown.get(0).band());
        assertEquals("unknown", unknown.get(0).componentKey());
    }

    private static Deployment deployment(long seq, String hash, DeploymentHealth health) {
        return Deployment.builder()
                .id("d" + seq)
                .localSequence(seq)
                .nodeId("n")
                .health(health)
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVendor("Temurin")
                .javaVersion("21")
                .components(List.of(new ComponentSnapshot(
                        new ComponentIdentity(
                                ComponentType.PLUGIN,
                                "PlugTraceFixtureSameVersion",
                                "2.4.0",
                                hash,
                                List.of(),
                                List.of(),
                                List.of(),
                                "Main",
                                "1.21"
                        ),
                        "plugins/same.jar",
                        10,
                        true,
                        true,
                        null
                )))
                .build();
    }
}
