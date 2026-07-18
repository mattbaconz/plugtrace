package dev.pluglabs.plugtrace.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Persists restore plans for offline finalize after a clean server stop. */
public final class RestoreJournalCodec {
    private static final Pattern FIELD = Pattern.compile("\"(\\w+)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"");
    private static final Pattern ACTION_BLOCK = Pattern.compile("\\{([^{}]*)\\}");

    private RestoreJournalCodec() {
    }

    public static Path planFile(Path dataFolder, String planId) {
        return dataFolder.resolve("restore-journal").resolve(planId + ".plan.json");
    }

    public static Path stageMarker(Path dataFolder, String planId) {
        return dataFolder.resolve("restore-journal").resolve(planId + ".stage");
    }

    public static Path pendingCompleteMarker(Path dataFolder) {
        return dataFolder.resolve("restore-journal").resolve("pending-complete.txt");
    }

    public static void writePlan(Path dataFolder, RestorePlan plan, String stage) throws IOException {
        Path journalDir = dataFolder.resolve("restore-journal");
        Files.createDirectories(journalDir);
        Path file = planFile(dataFolder, plan.id());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": \"").append(esc(plan.id())).append("\",\n");
        sb.append("  \"stage\": \"").append(esc(stage)).append("\",\n");
        sb.append("  \"status\": \"").append(plan.status().name()).append("\",\n");
        sb.append("  \"targetDeploymentId\": \"").append(esc(plan.targetDeploymentId())).append("\",\n");
        sb.append("  \"baselineDeploymentId\": \"").append(esc(plan.baselineDeploymentId())).append("\",\n");
        sb.append("  \"journalPath\": \"").append(esc(plan.journalPath())).append("\",\n");
        sb.append("  \"writtenAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"warnings\": [\n");
        for (int i = 0; i < plan.warnings().size(); i++) {
            sb.append("    \"").append(esc(plan.warnings().get(i))).append("\"");
            if (i + 1 < plan.warnings().size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append("  \"actions\": [\n");
        for (int i = 0; i < plan.actions().size(); i++) {
            RestorePlan.RestoreAction a = plan.actions().get(i);
            sb.append("    {\n");
            sb.append("      \"componentKey\": \"").append(esc(a.componentKey())).append("\",\n");
            sb.append("      \"kind\": \"").append(esc(a.kind())).append("\",\n");
            sb.append("      \"fromHash\": \"").append(esc(a.fromHash())).append("\",\n");
            sb.append("      \"toHash\": \"").append(esc(a.toHash())).append("\",\n");
            sb.append("      \"retainedPath\": \"").append(esc(a.retainedPath())).append("\",\n");
            sb.append("      \"livePath\": \"").append(esc(a.livePath())).append("\",\n");
            sb.append("      \"note\": \"").append(esc(a.note())).append("\"\n");
            sb.append("    }");
            if (i + 1 < plan.actions().size()) {
                sb.append(",");
            }
            sb.append("\n");
        }
        sb.append("  ]\n");
        sb.append("}\n");
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8);
        Files.writeString(stageMarker(dataFolder, plan.id()), stage, StandardCharsets.UTF_8);
        // Legacy short journal for humans
        Path shortJournal = journalDir.resolve(plan.id() + ".json");
        Files.writeString(shortJournal, "{\n  \"id\": \"" + esc(plan.id()) + "\",\n  \"stage\": \""
                + esc(stage) + "\",\n  \"status\": \"" + plan.status().name() + "\"\n}\n", StandardCharsets.UTF_8);
    }

    public static RestorePlan readPlan(Path planFile) throws IOException {
        String json = Files.readString(planFile);
        String id = field(json, "id");
        String statusRaw = field(json, "status");
        String target = field(json, "targetDeploymentId");
        String baseline = field(json, "baselineDeploymentId");
        String journalPath = field(json, "journalPath");
        RestorePlan.Status status = RestorePlan.Status.STAGED;
        try {
            status = RestorePlan.Status.valueOf(statusRaw);
        } catch (Exception ignored) {
            // keep STAGED
        }
        List<RestorePlan.RestoreAction> actions = new ArrayList<>();
        int actionsIdx = json.indexOf("\"actions\"");
        if (actionsIdx >= 0) {
            String actionsSection = json.substring(actionsIdx);
            Matcher blocks = ACTION_BLOCK.matcher(actionsSection);
            while (blocks.find()) {
                String block = blocks.group(1);
                if (!block.contains("\"kind\"")) {
                    continue;
                }
                actions.add(new RestorePlan.RestoreAction(
                        field("{" + block + "}", "componentKey"),
                        field("{" + block + "}", "kind"),
                        emptyToNull(field("{" + block + "}", "fromHash")),
                        emptyToNull(field("{" + block + "}", "toHash")),
                        emptyToNull(field("{" + block + "}", "retainedPath")),
                        emptyToNull(field("{" + block + "}", "livePath")),
                        emptyToNull(field("{" + block + "}", "note"))
                ));
            }
        }
        List<String> warnings = new ArrayList<>();
        return new RestorePlan(
                id,
                emptyToNull(target),
                emptyToNull(baseline),
                Instant.now(),
                status,
                actions,
                warnings,
                emptyToNull(journalPath)
        );
    }

    public static Path findLatestStagedPlan(Path dataFolder) throws IOException {
        Path dir = dataFolder.resolve("restore-journal");
        if (!Files.isDirectory(dir)) {
            return null;
        }
        Path latest = null;
        long latestTime = -1;
        try (var stream = Files.list(dir)) {
            for (Path p : stream.filter(f -> f.getFileName().toString().endsWith(".plan.json")).toList()) {
                String stage = Files.exists(stageMarker(dataFolder, p.getFileName().toString().replace(".plan.json", "")))
                        ? Files.readString(stageMarker(dataFolder, p.getFileName().toString().replace(".plan.json", ""))).trim()
                        : "";
                if (!"STAGED".equals(stage) && !"FINALIZE_PENDING".equals(stage)) {
                    // Still allow STAGED plan files
                    String content = Files.readString(p);
                    if (!content.contains("\"status\": \"STAGED\"") && !content.contains("\"stage\": \"STAGED\"")) {
                        continue;
                    }
                }
                long t = Files.getLastModifiedTime(p).toMillis();
                if (t > latestTime) {
                    latestTime = t;
                    latest = p;
                }
            }
        }
        return latest;
    }

    private static String field(String json, String name) {
        Matcher m = Pattern.compile("\"" + name + "\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"").matcher(json);
        if (m.find()) {
            return unescape(m.group(1));
        }
        return "";
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static String unescape(String value) {
        return value.replace("\\\"", "\"").replace("\\\\", "\\");
    }
}
