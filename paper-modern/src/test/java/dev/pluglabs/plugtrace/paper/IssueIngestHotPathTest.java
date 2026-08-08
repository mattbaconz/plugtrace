package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycle;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.IssueEvent;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IssueIngestHotPathTest {
    @TempDir
    Path tempDir;

    private PlugTraceService service;

    @BeforeEach
    void setUp() {
        service = new PlugTraceService(Logger.getLogger("plugtrace-test"), tempDir, new YamlConfiguration());
        service.seedDeploymentForTests(Deployment.builder()
                .id("dep-test")
                .localSequence(1)
                .nodeId("node-test")
                .stateFingerprint("fp")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.UNKNOWN)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVersion("21")
                .javaVendor("Temurin")
                .build());
    }

    @AfterEach
    void tearDown() {
        service.close();
    }

    @Test
    void firstSampleIsRecordedForNewFingerprint() {
        IssueEvent event = new IssueEvent(
                null,
                Instant.parse("2026-08-08T00:00:00Z"),
                "dep-test",
                "logger",
                "error",
                "java.lang.RuntimeException",
                "first boom",
                "java.lang.RuntimeException: first boom\n\tat com.example.Shop.run(Shop.java:1)",
                List.of("Shop"),
                "Server thread"
        );
        service.processReadyIssueForTests(event);
        List<Issue> issues = service.currentIssues();
        assertEquals(1, issues.size());
        assertEquals(1, issues.get(0).occurrenceCount());
        assertTrue(issues.get(0).sampleStack().contains("com.example.Shop"));
    }

    @Test
    void deferredDuplicatesBumpWithoutDroppingNewFingerprints() {
        RuntimeException boom = new RuntimeException("storm");
        Instant t0 = Instant.parse("2026-08-08T00:00:00Z");
        service.processDeferredExceptionForTests(t0, "warning", boom, "storm", List.of("Shop"), "Server thread");
        service.processDeferredExceptionForTests(t0.plusSeconds(1), "warning", boom, "storm", List.of("Shop"), "Server thread");
        service.processDeferredExceptionForTests(t0.plusSeconds(2), "warning", boom, "storm", List.of("Shop"), "Server thread");

        RuntimeException other = new RuntimeException("other");
        service.processDeferredExceptionForTests(t0.plusSeconds(3), "warning", other, "other", List.of("Other"), "Server thread");

        List<Issue> issues = service.currentIssues();
        assertEquals(2, issues.size());
        Issue storm = issues.stream().filter(i -> i.normalizedMessage().contains("storm")).findFirst().orElseThrow();
        Issue distinct = issues.stream().filter(i -> i.normalizedMessage().contains("other")).findFirst().orElseThrow();
        assertEquals(3, storm.occurrenceCount());
        assertEquals(1, distinct.occurrenceCount());
        assertFalse(storm.sampleStack().isBlank());
        assertFalse(distinct.sampleStack().isBlank());
    }
}
