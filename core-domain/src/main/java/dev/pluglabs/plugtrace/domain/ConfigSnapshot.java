package dev.pluglabs.plugtrace.domain;

import java.util.Objects;

/** Hash-only config capture by default — never store content in Phase 1. */
public final class ConfigSnapshot {
    private final String ownerComponent;
    private final String relativePath;
    private final String sha256;
    private final String captureLevel;
    private final long sizeBytes;
    private final int structuralKeyCount;

    public ConfigSnapshot(String ownerComponent, String relativePath, String sha256, String captureLevel) {
        this(ownerComponent, relativePath, sha256, captureLevel, -1, -1);
    }

    public ConfigSnapshot(String ownerComponent, String relativePath, String sha256, String captureLevel,
                          long sizeBytes, int structuralKeyCount) {
        this.ownerComponent = Objects.requireNonNull(ownerComponent, "ownerComponent");
        this.relativePath = Objects.requireNonNull(relativePath, "relativePath");
        this.sha256 = Objects.requireNonNull(sha256, "sha256");
        this.captureLevel = captureLevel == null ? "hash-only" : captureLevel;
        this.sizeBytes = sizeBytes;
        this.structuralKeyCount = structuralKeyCount;
    }

    public String ownerComponent() {
        return ownerComponent;
    }

    public String relativePath() {
        return relativePath;
    }

    public String sha256() {
        return sha256;
    }

    public String captureLevel() {
        return captureLevel;
    }

    public long sizeBytes() { return sizeBytes; }

    public int structuralKeyCount() { return structuralKeyCount; }

    public String key() {
        return ownerComponent + ":" + relativePath;
    }
}
