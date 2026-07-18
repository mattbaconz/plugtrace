package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class Annotation {
    private final String id;
    private final String deploymentId;
    private final Instant createdAt;
    private final String actor;
    private final String category;
    private final String text;
    private final String link;

    public Annotation(
            String id,
            String deploymentId,
            Instant createdAt,
            String actor,
            String category,
            String text,
            String link
    ) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.deploymentId = Objects.requireNonNull(deploymentId, "deploymentId");
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
        this.actor = actor == null ? "console" : actor;
        this.category = category == null ? "other" : category;
        this.text = text == null ? "" : text;
        this.link = link;
    }

    public String id() {
        return id;
    }

    public String deploymentId() {
        return deploymentId;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public String actor() {
        return actor;
    }

    public String category() {
        return category;
    }

    public String text() {
        return text;
    }

    public String link() {
        return link;
    }
}
