package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Identity written by PlugDev for test→production continuity. */
public final class PlugDevIdentity {
    public static final String SCHEMA_VERSION = "1";

    private final String schemaVersion;
    private final String gitCommit;
    private final boolean gitDirty;
    private final String buildSystem;
    private final String buildTask;
    private final String artifactHash;
    private final String projectName;
    private final String sessionId;
    private final String plugdevVersion;
    private final Instant recordedAt;

    public PlugDevIdentity(
            String schemaVersion,
            String gitCommit,
            boolean gitDirty,
            String buildSystem,
            String buildTask,
            String artifactHash,
            String projectName,
            String sessionId,
            String plugdevVersion,
            Instant recordedAt
    ) {
        this.schemaVersion = schemaVersion == null ? SCHEMA_VERSION : schemaVersion;
        this.gitCommit = gitCommit;
        this.gitDirty = gitDirty;
        this.buildSystem = buildSystem;
        this.buildTask = buildTask;
        this.artifactHash = artifactHash;
        this.projectName = projectName;
        this.sessionId = sessionId;
        this.plugdevVersion = plugdevVersion;
        this.recordedAt = recordedAt == null ? Instant.now() : recordedAt;
    }

    public String schemaVersion() {
        return schemaVersion;
    }

    public String gitCommit() {
        return gitCommit;
    }

    public boolean gitDirty() {
        return gitDirty;
    }

    public String buildSystem() {
        return buildSystem;
    }

    public String buildTask() {
        return buildTask;
    }

    public String artifactHash() {
        return artifactHash;
    }

    public String projectName() {
        return projectName;
    }

    public String sessionId() {
        return sessionId;
    }

    public String plugdevVersion() {
        return plugdevVersion;
    }

    public Instant recordedAt() {
        return recordedAt;
    }

    public String shortCommit() {
        if (gitCommit == null || gitCommit.isBlank()) {
            return "unknown";
        }
        return gitCommit.length() <= 8 ? gitCommit : gitCommit.substring(0, 8);
    }

    public String continuityKey() {
        return Objects.toString(gitCommit, "") + "|" + Objects.toString(artifactHash, "");
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("schemaVersion", schemaVersion);
        map.put("gitCommit", gitCommit);
        map.put("gitDirty", gitDirty);
        map.put("buildSystem", buildSystem);
        map.put("buildTask", buildTask);
        map.put("artifactHash", artifactHash);
        map.put("projectName", projectName);
        map.put("sessionId", sessionId);
        map.put("plugdevVersion", plugdevVersion);
        map.put("recordedAt", recordedAt.toString());
        return map;
    }

    public static PlugDevIdentity fromMap(Map<String, ?> map) {
        if (map == null || map.isEmpty()) {
            return null;
        }
        Instant recorded = Instant.now();
        Object rawTime = map.get("recordedAt");
        if (rawTime != null) {
            try {
                recorded = Instant.parse(String.valueOf(rawTime));
            } catch (Exception ignored) {
                // keep now
            }
        }
        return new PlugDevIdentity(
                str(map.get("schemaVersion")),
                str(map.get("gitCommit")),
                bool(map.get("gitDirty")),
                str(map.get("buildSystem")),
                str(map.get("buildTask")),
                str(map.get("artifactHash")),
                str(map.get("projectName")),
                str(map.get("sessionId")),
                str(map.get("plugdevVersion")),
                recorded
        );
    }

    private static String str(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static boolean bool(Object value) {
        if (value instanceof Boolean b) {
            return b;
        }
        return value != null && Boolean.parseBoolean(String.valueOf(value));
    }
}
