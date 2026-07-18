package dev.pluglabs.plugtrace.api;

import java.util.Objects;

public record MigrationRecord(
        String pluginId,
        String fromSchema,
        String toSchema,
        String summary,
        RollbackSafety rollbackSafety
) {
    public MigrationRecord {
        Objects.requireNonNull(pluginId, "pluginId");
        fromSchema = fromSchema == null ? "unknown" : fromSchema;
        toSchema = toSchema == null ? "unknown" : toSchema;
        summary = summary == null ? "" : summary;
        rollbackSafety = rollbackSafety == null ? RollbackSafety.UNKNOWN : rollbackSafety;
    }
}
