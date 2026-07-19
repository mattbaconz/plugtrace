package dev.pluglabs.plugtrace.report;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.ConfidenceBand;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.Sha256Hasher;
import dev.pluglabs.plugtrace.domain.Suspect;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ReportService {
    public static final String SCHEMA_VERSION = "1.0.0";

    private final ObjectMapper mapper;
    private final RedactionService redaction = new RedactionService();
    private final HtmlReportRenderer htmlRenderer = new HtmlReportRenderer();
    private final DiscordReportFormatter discordFormatter = new DiscordReportFormatter();
    private final GitHubReportFormatter githubFormatter = new GitHubReportFormatter();

    public ReportService() {
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .enable(SerializationFeature.INDENT_OUTPUT);
    }

    public ReportArtifacts generate(
            Deployment current,
            Deployment baseline,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects,
            String baselineDescription
    ) {
        return generate(new ReportRequest(
                current, baseline, changes, issues, suspects, List.of(),
                baselineDescription, Map.of("detected", false), Map.of("type", "deployment"),
                Map.of(), null
        ));
    }

    public ReportArtifacts generate(ReportRequest request) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("schemaVersion", SCHEMA_VERSION);
        json.put("generatedAt", Instant.now().toString());
        json.put("privacyMode", request.privacyMode());
        json.put("privacyDeclaration", Map.of(
                "localFirst", true,
                "automaticUpload", false,
                "configValuesIncluded", false,
                "secretRedaction", "best-effort",
                "mode", request.privacyMode()
        ));
        json.put("scope", request.scope());
        json.put("platform", request.platform());
        if (!request.release().isEmpty()) {
            json.put("release", request.release());
        }
        if (!request.pluginFields().isEmpty()) {
            json.put("pluginFields", request.pluginFields());
        }
        json.put("preview", request.previewSections());
        json.put("redactionWarnings", List.of(
                "Config contents are not included (hash-only).",
                "Log samples are redacted for secrets/tokens.",
                "Nothing is uploaded automatically."
        ));

        Map<String, Object> executive = buildExecutiveSummary(request.suspects(), request.issues());
        json.put("executiveSummary", executive);

        Deployment current = request.current();
        Map<String, Object> deployment = new LinkedHashMap<>();
        deployment.put("id", current.id());
        deployment.put("sequence", current.localSequence());
        deployment.put("health", current.health().name());
        deployment.put("server", current.serverImplementation());
        deployment.put("minecraft", current.minecraftVersion());
        deployment.put("java", current.javaVendor() + " " + current.javaVersion());
        deployment.put("stateFingerprint", current.stateFingerprint());
        deployment.put("pluginCount", current.components().size());
        json.put("deployment", deployment);

        Map<String, Object> baselineMap = new LinkedHashMap<>();
        baselineMap.put("description", request.baselineDescription());
        if (request.baseline() != null) {
            baselineMap.put("id", request.baseline().id());
            baselineMap.put("sequence", request.baseline().localSequence());
            baselineMap.put("health", request.baseline().health().name());
        }
        json.put("baseline", baselineMap);

        json.put("changes", request.changes().stream().map(this::changeMap).toList());
        json.put("issues", request.issues().stream().map(this::issueMap).toList());
        json.put("suspects", request.suspects().stream().map(this::suspectMap).toList());
        json.put("verification", request.verification());
        json.put("incidents", request.incidents());
        json.put("annotations", request.annotations().stream().map(this::annotationMap).toList());
        json.put("spark", request.spark());

        try {
            String jsonText = mapper.writeValueAsString(json);
            String markdown = toMarkdown(request, executive);
            String html = htmlRenderer.render(request, executive, redaction);
            String discord = discordFormatter.format(request, executive, redaction);
            String github = githubFormatter.format(request, executive, redaction);
            String artifactHash = Sha256Hasher.hashString(jsonText);
            return new ReportArtifacts(jsonText, markdown, html, discord, github, artifactHash, SCHEMA_VERSION, request.previewSections());
        } catch (Exception e) {
            throw new IllegalStateException("Failed to render PlugTrace report", e);
        }
    }

    private Map<String, Object> buildExecutiveSummary(List<Suspect> suspects, List<Issue> issues) {
        Map<String, Object> executive = new LinkedHashMap<>();
        long newIssues = issues.stream().filter(i -> i.status().name().equals("NEW")).count();
        executive.put("newIssueCount", newIssues);
        executive.put("issueCount", issues.size());
        if (suspects.isEmpty()
                || (suspects.size() == 1 && suspects.get(0).band() == ConfidenceBand.UNKNOWN
                && "unknown".equalsIgnoreCase(suspects.get(0).componentKey()))) {
            executive.put("strongestSuspect", "Unknown");
            executive.put("band", ConfidenceBand.UNKNOWN.name());
            executive.put("evidence", List.of("Insufficient evidence to name a suspect."));
            executive.put("unknown", true);
            return executive;
        }
        Suspect top = suspects.get(0);
        executive.put("strongestSuspect", top.componentKey());
        executive.put("band", top.band().name());
        executive.put("summary", top.changeSummary());
        executive.put("unknown", false);
        List<String> bullets = new ArrayList<>();
        for (var evidence : top.supporting()) {
            if (bullets.size() >= 3) {
                break;
            }
            bullets.add(evidence.explanation());
        }
        executive.put("evidence", bullets);
        return executive;
    }

    private Map<String, Object> changeMap(Change change) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", change.type().name());
        map.put("component", change.componentKey());
        map.put("before", change.before());
        map.put("after", change.after());
        map.put("explanation", change.explanation());
        map.put("significance", change.significance());
        return map;
    }

    private Map<String, Object> issueMap(Issue issue) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("fingerprint", issue.fingerprint());
        map.put("status", issue.status().name());
        map.put("regressionClass", issue.regressionClass().name());
        map.put("type", issue.normalizedType());
        map.put("message", redaction.redact(issue.normalizedMessage()));
        map.put("count", issue.occurrenceCount());
        map.put("ownership", issue.ownershipCandidates());
        map.put("sample", redaction.redact(issue.sampleStack()));
        return map;
    }

    private Map<String, Object> suspectMap(Suspect suspect) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("rank", suspect.rank());
        map.put("component", suspect.componentKey());
        map.put("band", suspect.band().name());
        map.put("summary", suspect.changeSummary());
        map.put("supporting", suspect.supporting().stream().map(e -> Map.of(
                "source", e.source(),
                "explanation", e.explanation()
        )).toList());
        map.put("contradictions", suspect.contradictions().stream().map(e -> Map.of(
                "source", e.source(),
                "explanation", e.explanation()
        )).toList());
        return map;
    }

    private Map<String, Object> annotationMap(Annotation annotation) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", annotation.id());
        map.put("category", annotation.category());
        map.put("actor", annotation.actor());
        map.put("createdAt", annotation.createdAt().toString());
        map.put("text", redaction.redact(annotation.text()));
        map.put("link", annotation.link());
        return map;
    }

    private String toMarkdown(ReportRequest request, Map<String, Object> executive) {
        Deployment current = request.current();
        StringBuilder md = new StringBuilder();
        md.append("# PlugTrace Report\n\n");
        md.append("- Schema: `").append(SCHEMA_VERSION).append("`\n");
        md.append("- Scope: `").append(request.scope().getOrDefault("type", "deployment")).append("`\n");
        md.append("- Deployment: `#").append(current.localSequence()).append("` (`").append(current.id()).append("`)\n");
        md.append("- Health: `").append(current.health()).append("`\n");
        md.append("- Baseline: ").append(request.baselineDescription()).append("\n");
        md.append("- Strongest suspect: **").append(executive.get("strongestSuspect")).append("** [`")
                .append(executive.get("band")).append("`]\n");
        md.append("- Privacy: hash-only configs; secrets redacted; nothing uploaded\n\n");

        md.append("## Executive summary\n\n");
        Object evidenceObj = executive.get("evidence");
        if (evidenceObj instanceof List<?> list) {
            for (Object item : list) {
                md.append("- ").append(item).append('\n');
            }
        }
        md.append('\n');

        md.append("## Changes\n\n");
        if (request.changes().isEmpty()) {
            md.append("_No typed changes detected._\n\n");
        } else {
            for (Change change : request.changes()) {
                md.append("- **").append(change.type()).append("** `").append(change.componentKey()).append("` — ")
                        .append(change.explanation()).append("\n");
            }
            md.append('\n');
        }

        md.append("## Issues\n\n");
        if (request.issues().isEmpty()) {
            md.append("_No issues recorded for this deployment._\n\n");
        } else {
            for (Issue issue : request.issues()) {
                md.append("- `").append(issue.regressionClass()).append("` / `").append(issue.status()).append("` — ")
                        .append(redaction.redact(issue.normalizedMessage())).append(" (×")
                        .append(issue.occurrenceCount()).append(")\n");
            }
            md.append('\n');
        }

        md.append("## Verification\n\n");
        if (request.verification() == null) {
            md.append("_Verification has not completed._\n\n");
        } else {
            md.append("- Health: `").append(request.verification().health()).append("`\n");
            for (var check : request.verification().checks()) {
                md.append("- `").append(check.status()).append("` ").append(check.displayName())
                        .append(": ").append(redaction.redact(check.summary())).append('\n');
            }
            md.append('\n');
        }

        md.append("## Incidents\n\n");
        if (request.incidents().isEmpty()) {
            md.append("_No incidents attached._\n\n");
        } else {
            for (var incident : request.incidents()) {
                md.append("- `").append(incident.status()).append("` ")
                        .append(redaction.redact(incident.summary())).append('\n');
            }
            md.append('\n');
        }

        md.append("## Suspects\n\n");
        for (Suspect suspect : request.suspects()) {
            md.append(suspect.rank()).append(". **").append(suspect.componentKey()).append("** — band `")
                    .append(suspect.band()).append("`\n");
            md.append("   - ").append(suspect.changeSummary()).append('\n');
            for (var evidence : suspect.supporting()) {
                md.append("   - + ").append(evidence.explanation()).append('\n');
            }
            for (var evidence : suspect.contradictions()) {
                md.append("   - − ").append(evidence.explanation()).append('\n');
            }
        }

        md.append("\n## Annotations\n\n");
        if (request.annotations().isEmpty()) {
            md.append("_No annotations._\n\n");
        } else {
            for (Annotation annotation : request.annotations()) {
                md.append("- `").append(annotation.category()).append("` by ")
                        .append(annotation.actor()).append(": ")
                        .append(redaction.redact(annotation.text())).append('\n');
            }
            md.append('\n');
        }

        md.append("## Spark\n\n");
        md.append("- Detected: `").append(request.spark().getOrDefault("detected", false)).append("`\n");
        if (request.spark().containsKey("profileUrl")) {
            md.append("- Profile: ").append(request.spark().get("profileUrl")).append('\n');
        }
        if (!request.release().isEmpty()) {
            md.append("\n## Release identity (PlugDev)\n\n");
            Object commit = request.release().get("gitCommit");
            Object project = request.release().get("projectName");
            if (commit != null) {
                md.append("- Git commit: `").append(commit).append("`\n");
            }
            if (project != null) {
                md.append("- Project: `").append(project).append("`\n");
            }
            if (request.release().get("artifactHash") != null) {
                md.append("- Artifact hash: `").append(request.release().get("artifactHash")).append("`\n");
            }
        }
        if (!request.pluginFields().isEmpty()) {
            md.append("\n## Plugin safe fields\n\n");
            for (Map.Entry<String, Object> entry : request.pluginFields().entrySet()) {
                md.append("- `").append(entry.getKey()).append("`: ")
                        .append(redaction.redact(String.valueOf(entry.getValue()))).append('\n');
            }
        }
        md.append("\n_Unknown is a valid outcome when evidence is weak._\n");
        return md.toString();
    }

    public record ReportArtifacts(
            String json,
            String markdown,
            String html,
            String discord,
            String github,
            String artifactHash,
            String schemaVersion,
            List<String> previewSections
    ) {
    }
}
