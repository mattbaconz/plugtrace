package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable restore preview / staged restore plan (Phase 5). */
public final class RestorePlan {
    public enum Status {
        PREVIEW,
        STAGED,
        APPLIED,
        REVERTED,
        FAILED,
        INTERRUPTED
    }

    private final String id;
    private final String targetDeploymentId;
    private final String baselineDeploymentId;
    private final Instant createdAt;
    private final Status status;
    private final List<RestoreAction> actions;
    private final List<String> warnings;
    private final String journalPath;

    public RestorePlan(
            String id,
            String targetDeploymentId,
            String baselineDeploymentId,
            Instant createdAt,
            Status status,
            List<RestoreAction> actions,
            List<String> warnings,
            String journalPath
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.targetDeploymentId = targetDeploymentId;
        this.baselineDeploymentId = baselineDeploymentId;
        this.createdAt = createdAt == null ? Instant.now() : createdAt;
        this.status = status == null ? Status.PREVIEW : status;
        this.actions = actions == null ? List.of() : List.copyOf(actions);
        this.warnings = warnings == null ? List.of() : List.copyOf(warnings);
        this.journalPath = journalPath;
    }

    public String id() {
        return id;
    }

    public String targetDeploymentId() {
        return targetDeploymentId;
    }

    public String baselineDeploymentId() {
        return baselineDeploymentId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public Status status() {
        return status;
    }

    public List<RestoreAction> actions() {
        return actions;
    }

    public List<String> warnings() {
        return warnings;
    }

    public String journalPath() {
        return journalPath;
    }

    public RestorePlan withStatus(Status newStatus) {
        return new RestorePlan(id, targetDeploymentId, baselineDeploymentId, createdAt, newStatus, actions, warnings, journalPath);
    }

    public Map<String, Object> toMap() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", id);
        map.put("targetDeploymentId", targetDeploymentId);
        map.put("baselineDeploymentId", baselineDeploymentId);
        map.put("createdAt", createdAt.toString());
        map.put("status", status.name());
        map.put("journalPath", journalPath);
        List<Map<String, Object>> actionMaps = new ArrayList<>();
        for (RestoreAction action : actions) {
            actionMaps.add(action.toMap());
        }
        map.put("actions", actionMaps);
        map.put("warnings", warnings);
        return map;
    }

    public record RestoreAction(
            String componentKey,
            String kind,
            String fromHash,
            String toHash,
            String retainedPath,
            String livePath,
            String note
    ) {
        public Map<String, Object> toMap() {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("componentKey", componentKey);
            map.put("kind", kind);
            map.put("fromHash", fromHash);
            map.put("toHash", toHash);
            map.put("retainedPath", retainedPath);
            map.put("livePath", livePath);
            map.put("note", note);
            return map;
        }
    }
}
