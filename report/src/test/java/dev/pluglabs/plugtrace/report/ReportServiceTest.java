package dev.pluglabs.plugtrace.report;

import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.ChangeType;
import dev.pluglabs.plugtrace.domain.ConfidenceBand;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycle;
import dev.pluglabs.plugtrace.domain.Evidence;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.IssueStatus;
import dev.pluglabs.plugtrace.domain.RegressionClass;
import dev.pluglabs.plugtrace.domain.Suspect;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportServiceTest {
    @Test
    void acceptsOptionalNullReleaseFieldsFromLocalBuildIdentity() {
        Map<String, Object> release = new LinkedHashMap<>();
        release.put("project", "PlugTrace");
        release.put("branch", null);

        ReportRequest request = new ReportRequest(
                null, null, List.of(), List.of(), List.of(), List.of(), "",
                Map.of("detected", false), Map.of("type", "deployment"), Map.of(), release,
                Map.of(), List.of()
        );

        assertEquals("PlugTrace", request.release().get("project"));
        assertTrue(request.release().containsKey("branch"));
        assertNull(request.release().get("branch"));
    }

    @Test
    void rendersJsonMarkdownHtmlAndDiscord() {
        Deployment current = Deployment.builder()
                .id("d2")
                .localSequence(2)
                .nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.DEGRADED)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVersion("21")
                .javaVendor("Temurin")
                .stateFingerprint("fp")
                .build();

        ReportService.ReportArtifacts artifacts = new ReportService().generate(new ReportRequest(
                current,
                null,
                List.of(new Change(
                        ChangeType.BINARY_CHANGED_SAME_VERSION,
                        "PLUGIN:Shop",
                        "aaa",
                        "bbb",
                        "Version remains 2.4.0, but binary hash changed.",
                        90
                )),
                List.of(new Issue(
                        "i1", "fp1", "exception", "token=supersecret failed",
                        List.of("Shop"), Instant.now(), Instant.now(),
                        IssueStatus.NEW, "error", 3, "password=leak",
                        RegressionClass.NEW_ISSUE
                )),
                List.of(new Suspect(
                        "PLUGIN:Shop",
                        "binary changed",
                        ConfidenceBand.MEDIUM,
                        1,
                        List.of(new Evidence("change", "supporting", "hash changed", 90)),
                        List.of()
                )),
                List.of(new Annotation(
                        "a1", "d2", Instant.now(), "ops", "REDACTION_CAMPAIGN",
                        "password=SuperSecret123! aws_access_key_id=AKIAIOSFODNN7EXAMPLE",
                        null
                )),
                "No reliable baseline exists yet.",
                Map.of("detected", false),
                Map.of("type", "deployment"),
                Map.of("fork", "paper"),
                null
        ));

        assertTrue(artifacts.json().contains("1.0.0"));
        assertTrue(artifacts.json().contains("executiveSummary"));
        assertTrue(artifacts.json().contains("privacyDeclaration"));
        assertTrue(artifacts.json().contains("\"verification\""));
        assertTrue(artifacts.json().contains("\"incidents\""));
        assertTrue(artifacts.markdown().contains("PlugTrace Report"));
        assertTrue(artifacts.html().contains("<!DOCTYPE html>"));
        assertTrue(artifacts.discord().length() <= 2000);
        assertTrue(artifacts.discord().contains("Annotations:"));
        assertTrue(artifacts.github().contains("### Annotations"));
        assertTrue(artifacts.discord().contains("<redacted>") || artifacts.discord().contains("<aws-key>"));
        assertTrue(artifacts.github().contains("<redacted>") || artifacts.github().contains("<aws-key>"));
        assertTrue(!artifacts.discord().contains("SuperSecret123"));
        assertTrue(!artifacts.github().contains("AKIAIOSFODNN7EXAMPLE"));
        assertTrue(artifacts.json().contains("<redacted>") || artifacts.markdown().contains("<redacted>"));
    }

    @Test
    void includesReleaseIdentityWhenPresent() {
        Deployment current = Deployment.builder()
                .id("d2")
                .localSequence(2)
                .nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.UNKNOWN)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVersion("21")
                .javaVendor("Temurin")
                .stateFingerprint("fp")
                .build();

        ReportService.ReportArtifacts artifacts = new ReportService().generate(new ReportRequest(
                current,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "No baseline",
                Map.of("detected", false),
                Map.of("type", "deployment"),
                Map.of("fork", "paper", "artifact", "paper-modern"),
                Map.of(
                        "schemaVersion", "1",
                        "gitCommit", "abcdef1234567890",
                        "projectName", "DemoShop",
                        "artifactHash", "deadbeef"
                ),
                null
        ));

        assertTrue(artifacts.json().contains("1.0.0"));
        assertTrue(artifacts.json().contains("abcdef1234567890"));
        assertTrue(artifacts.json().contains("DemoShop"));
        assertTrue(artifacts.markdown().contains("Release identity"));
    }

    @Test
    void includesPluginFieldsWhenPresent() {
        Deployment current = Deployment.builder()
                .id("d3")
                .localSequence(3)
                .nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.UNKNOWN)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVersion("21")
                .javaVendor("Temurin")
                .stateFingerprint("fp")
                .build();

        ReportService.ReportArtifacts artifacts = new ReportService().generate(new ReportRequest(
                current,
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                "No baseline",
                Map.of("detected", false),
                Map.of("type", "deployment"),
                Map.of("fork", "paper"),
                Map.of(),
                Map.of("myplugin:mode", "skyblock"),
                null
        ));

        assertTrue(artifacts.json().contains("pluginFields"));
        assertTrue(artifacts.json().contains("skyblock"));
        assertTrue(artifacts.markdown().contains("Plugin safe fields"));
    }
}
