package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.Objects;

/** Immutable named reference to a deployment. A checkpoint is not a full backup. */
public record Checkpoint(String id, String deploymentId, String name, Instant createdAt, String actor) {
    public Checkpoint {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(deploymentId, "deploymentId");
        name = name == null || name.isBlank() ? "checkpoint" : name.trim();
        Objects.requireNonNull(createdAt, "createdAt");
        actor = actor == null || actor.isBlank() ? "console" : actor;
    }
}
