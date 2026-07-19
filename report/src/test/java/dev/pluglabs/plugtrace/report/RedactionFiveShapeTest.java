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

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Offline five-shape adversarial redaction campaign (JSON/MD/HTML/Discord/GitHub). */
class RedactionFiveShapeTest {
    private static final String SEED = """
            password=SuperSecret123!
            discord_webhook=https://discord.com/api/webhooks/1234567890/abcdefghijklmnopqrstuvwxyz
            Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig
            aws_access_key_id=AKIAIOSFODNN7EXAMPLE
            player_uuid=550e8400-e29b-41d4-a716-446655440000
            player_ip=203.0.113.42
            path=C:\\Users\\operator\\.minecraft\\secrets.yml
            """;

    private static final String[] RESIDUALS = {
            "SuperSecret123",
            "discord.com/api/webhooks/1234567890",
            "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.payload.sig",
            "AKIAIOSFODNN7EXAMPLE",
            "550e8400-e29b-41d4-a716-446655440000",
            "203.0.113.42",
            "C:\\Users\\operator"
    };

    @Test
    void allFiveExportShapesRedactSeedSecrets() throws Exception {
        Deployment current = Deployment.builder()
                .id("d13")
                .localSequence(13)
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
                        "i1", "fp1", "exception", SEED.trim(),
                        List.of("Shop"), Instant.now(), Instant.now(),
                        IssueStatus.NEW, "error", 3, SEED.trim(),
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
                        "a1", "d13", Instant.now(), "ops", "REDACTION_CAMPAIGN", SEED.trim(), null
                )),
                "baseline",
                Map.of("detected", false),
                Map.of("type", "deployment"),
                Map.of("fork", "paper"),
                null
        ));

        Path out = Files.createTempDirectory("plugtrace-redaction-five");
        Files.writeString(out.resolve("deployment-13.json"), artifacts.json());
        Files.writeString(out.resolve("deployment-13.md"), artifacts.markdown());
        Files.writeString(out.resolve("deployment-13.html"), artifacts.html());
        Files.writeString(out.resolve("deployment-13.discord.txt"), artifacts.discord());
        Files.writeString(out.resolve("deployment-13.github.md"), artifacts.github());

        assertTrue(artifacts.discord().contains("Annotations:"));
        assertTrue(artifacts.github().contains("### Annotations"));

        for (String shape : List.of(
                artifacts.json(), artifacts.markdown(), artifacts.html(),
                artifacts.discord(), artifacts.github())) {
            for (String residual : RESIDUALS) {
                assertFalse(shape.contains(residual),
                        "Residual secret leaked: " + residual + " in export shape");
            }
        }
    }
}
