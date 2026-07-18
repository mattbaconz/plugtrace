package dev.pluglabs.plugtrace.domain;

import java.util.Objects;

public final class ComponentSnapshot {
    private final ComponentIdentity identity;
    private final String relativePath;
    private final long sizeBytes;
    private final boolean loaded;
    private final boolean enabled;
    private final String loadFailure;
    /** Absolute filesystem path when known (for retention/restore); may be null. */
    private final String absolutePath;

    public ComponentSnapshot(
            ComponentIdentity identity,
            String relativePath,
            long sizeBytes,
            boolean loaded,
            boolean enabled,
            String loadFailure
    ) {
        this(identity, relativePath, sizeBytes, loaded, enabled, loadFailure, null);
    }

    public ComponentSnapshot(
            ComponentIdentity identity,
            String relativePath,
            long sizeBytes,
            boolean loaded,
            boolean enabled,
            String loadFailure,
            String absolutePath
    ) {
        this.identity = Objects.requireNonNull(identity, "identity");
        this.relativePath = relativePath == null ? "" : relativePath;
        this.sizeBytes = sizeBytes;
        this.loaded = loaded;
        this.enabled = enabled;
        this.loadFailure = loadFailure;
        this.absolutePath = absolutePath;
    }

    public ComponentIdentity identity() {
        return identity;
    }

    public String relativePath() {
        return relativePath;
    }

    public long sizeBytes() {
        return sizeBytes;
    }

    public boolean loaded() {
        return loaded;
    }

    public boolean enabled() {
        return enabled;
    }

    public String loadFailure() {
        return loadFailure;
    }

    public String absolutePath() {
        return absolutePath;
    }

    public String jarPathForRestore() {
        return absolutePath != null && !absolutePath.isBlank() ? absolutePath : relativePath;
    }
}
