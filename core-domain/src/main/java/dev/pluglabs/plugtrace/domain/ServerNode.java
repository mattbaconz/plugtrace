package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.Objects;

/** One server/proxy identity. Path alone is never the sole identity. */
public final class ServerNode {
    private final String id;
    private final String name;
    private final String platformFamily;
    private final String environment;
    private final Instant createdAt;

    public ServerNode(String id, String name, String platformFamily, String environment, Instant createdAt) {
        this.id = Objects.requireNonNull(id, "id");
        this.name = Objects.requireNonNull(name, "name");
        this.platformFamily = Objects.requireNonNull(platformFamily, "platformFamily");
        this.environment = environment == null ? "unknown" : environment;
        this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    }

    public String id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String platformFamily() {
        return platformFamily;
    }

    public String environment() {
        return environment;
    }

    public Instant createdAt() {
        return createdAt;
    }
}
