package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Immutable record of one attempted server runtime under one observed software state. */
public final class Deployment {
    private final String id;
    private final long localSequence;
    private final String nodeId;
    private final String parentId;
    private final String stateFingerprint;
    private final Instant startedAt;
    private final Instant endedAt;
    private final Instant startupReadyAt;
    private final long startupReadyMillis;
    private final List<String> crashReportReferences;
    private final DeploymentLifecycle lifecycle;
    private final DeploymentHealth health;
    private final List<String> healthReasons;
    private final boolean complete;
    private final List<String> tags;
    private final String serverImplementation;
    private final String minecraftVersion;
    private final String javaVersion;
    private final String javaVendor;
    private final List<ComponentSnapshot> components;
    private final List<ConfigSnapshot> configs;
    private final int schemaVersion;

    private Deployment(Builder builder) {
        this.id = builder.id;
        this.localSequence = builder.localSequence;
        this.nodeId = builder.nodeId;
        this.parentId = builder.parentId;
        this.stateFingerprint = builder.stateFingerprint;
        this.startedAt = builder.startedAt;
        this.endedAt = builder.endedAt;
        this.startupReadyAt = builder.startupReadyAt;
        this.startupReadyMillis = builder.startupReadyMillis;
        this.crashReportReferences = List.copyOf(builder.crashReportReferences);
        this.lifecycle = builder.lifecycle;
        this.health = builder.health;
        this.healthReasons = List.copyOf(builder.healthReasons);
        this.complete = builder.complete;
        this.tags = List.copyOf(builder.tags);
        this.serverImplementation = builder.serverImplementation;
        this.minecraftVersion = builder.minecraftVersion;
        this.javaVersion = builder.javaVersion;
        this.javaVendor = builder.javaVendor;
        this.components = List.copyOf(builder.components);
        this.configs = List.copyOf(builder.configs);
        this.schemaVersion = builder.schemaVersion;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String id() {
        return id;
    }

    public long localSequence() {
        return localSequence;
    }

    public String nodeId() {
        return nodeId;
    }

    public String parentId() {
        return parentId;
    }

    public String stateFingerprint() {
        return stateFingerprint;
    }

    public Instant startedAt() {
        return startedAt;
    }

    public Instant endedAt() {
        return endedAt;
    }

    public Instant startupReadyAt() {
        return startupReadyAt;
    }

    public long startupReadyMillis() {
        return startupReadyMillis;
    }

    public List<String> crashReportReferences() {
        return crashReportReferences;
    }

    public DeploymentLifecycle lifecycle() {
        return lifecycle;
    }

    public DeploymentHealth health() {
        return health;
    }

    public List<String> healthReasons() {
        return healthReasons;
    }

    public boolean complete() {
        return complete;
    }

    public List<String> tags() {
        return tags;
    }

    public String serverImplementation() {
        return serverImplementation;
    }

    public String minecraftVersion() {
        return minecraftVersion;
    }

    public String javaVersion() {
        return javaVersion;
    }

    public String javaVendor() {
        return javaVendor;
    }

    public List<ComponentSnapshot> components() {
        return components;
    }

    public List<ConfigSnapshot> configs() {
        return configs;
    }

    public int schemaVersion() {
        return schemaVersion;
    }

    public Deployment withHealth(DeploymentHealth health, List<String> reasons) {
        return copyBuilder()
                .health(health)
                .healthReasons(reasons)
                .build();
    }

    public Deployment withTags(List<String> newTags) {
        return copyBuilder().tags(newTags).build();
    }

    public Deployment withReadyEvidence(Instant readyAt, long readyMillis) {
        if (readyMillis < 0) {
            throw new IllegalArgumentException("readyMillis must be non-negative");
        }
        return copyBuilder()
                .startupReadyAt(Objects.requireNonNull(readyAt, "readyAt"))
                .startupReadyMillis(readyMillis)
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .complete(false)
                .build();
    }

    public Deployment withTermination(
            Instant terminatedAt, DeploymentLifecycle termination, List<String> crashReports) {
        if (termination != DeploymentLifecycle.STOPPED_CLEANLY
                && termination != DeploymentLifecycle.CRASHED
                && termination != DeploymentLifecycle.KILLED
                && termination != DeploymentLifecycle.INCOMPLETE) {
            throw new IllegalArgumentException("Not a terminal deployment lifecycle: " + termination);
        }
        return copyBuilder()
                .endedAt(Objects.requireNonNull(terminatedAt, "terminatedAt"))
                .lifecycle(termination)
                .complete(termination == DeploymentLifecycle.STOPPED_CLEANLY)
                .crashReportReferences(crashReports)
                .build();
    }

    private Builder copyBuilder() {
        return builder()
                .id(id)
                .localSequence(localSequence)
                .nodeId(nodeId)
                .parentId(parentId)
                .stateFingerprint(stateFingerprint)
                .startedAt(startedAt)
                .endedAt(endedAt)
                .startupReadyAt(startupReadyAt)
                .startupReadyMillis(startupReadyMillis)
                .crashReportReferences(crashReportReferences)
                .lifecycle(lifecycle)
                .health(health)
                .healthReasons(healthReasons)
                .complete(complete)
                .tags(tags)
                .serverImplementation(serverImplementation)
                .minecraftVersion(minecraftVersion)
                .javaVersion(javaVersion)
                .javaVendor(javaVendor)
                .components(components)
                .configs(configs)
                .schemaVersion(schemaVersion);
    }

    public static final class Builder {
        private String id = UUID.randomUUID().toString();
        private long localSequence;
        private String nodeId;
        private String parentId;
        private String stateFingerprint = "";
        private Instant startedAt = Instant.now();
        private Instant endedAt;
        private Instant startupReadyAt;
        private long startupReadyMillis = -1L;
        private List<String> crashReportReferences = new ArrayList<>();
        private DeploymentLifecycle lifecycle = DeploymentLifecycle.DETECTED;
        private DeploymentHealth health = DeploymentHealth.UNKNOWN;
        private List<String> healthReasons = new ArrayList<>();
        private boolean complete = true;
        private List<String> tags = new ArrayList<>();
        private String serverImplementation = "unknown";
        private String minecraftVersion = "unknown";
        private String javaVersion = "unknown";
        private String javaVendor = "unknown";
        private List<ComponentSnapshot> components = new ArrayList<>();
        private List<ConfigSnapshot> configs = new ArrayList<>();
        private int schemaVersion = 1;

        public Builder id(String id) {
            this.id = Objects.requireNonNull(id);
            return this;
        }

        public Builder localSequence(long localSequence) {
            this.localSequence = localSequence;
            return this;
        }

        public Builder nodeId(String nodeId) {
            this.nodeId = Objects.requireNonNull(nodeId);
            return this;
        }

        public Builder parentId(String parentId) {
            this.parentId = parentId;
            return this;
        }

        public Builder stateFingerprint(String stateFingerprint) {
            this.stateFingerprint = stateFingerprint == null ? "" : stateFingerprint;
            return this;
        }

        public Builder startedAt(Instant startedAt) {
            this.startedAt = Objects.requireNonNull(startedAt);
            return this;
        }

        public Builder endedAt(Instant endedAt) {
            this.endedAt = endedAt;
            return this;
        }

        public Builder startupReadyAt(Instant startupReadyAt) {
            this.startupReadyAt = startupReadyAt;
            return this;
        }

        public Builder startupReadyMillis(long startupReadyMillis) {
            this.startupReadyMillis = startupReadyMillis;
            return this;
        }

        public Builder crashReportReferences(List<String> crashReportReferences) {
            this.crashReportReferences = crashReportReferences == null
                    ? new ArrayList<>() : new ArrayList<>(crashReportReferences);
            return this;
        }

        public Builder lifecycle(DeploymentLifecycle lifecycle) {
            this.lifecycle = Objects.requireNonNull(lifecycle);
            return this;
        }

        public Builder health(DeploymentHealth health) {
            this.health = Objects.requireNonNull(health);
            return this;
        }

        public Builder healthReasons(List<String> healthReasons) {
            this.healthReasons = healthReasons == null ? new ArrayList<>() : new ArrayList<>(healthReasons);
            return this;
        }

        public Builder complete(boolean complete) {
            this.complete = complete;
            return this;
        }

        public Builder tags(List<String> tags) {
            this.tags = tags == null ? new ArrayList<>() : new ArrayList<>(tags);
            return this;
        }

        public Builder serverImplementation(String serverImplementation) {
            this.serverImplementation = serverImplementation;
            return this;
        }

        public Builder minecraftVersion(String minecraftVersion) {
            this.minecraftVersion = minecraftVersion;
            return this;
        }

        public Builder javaVersion(String javaVersion) {
            this.javaVersion = javaVersion;
            return this;
        }

        public Builder javaVendor(String javaVendor) {
            this.javaVendor = javaVendor;
            return this;
        }

        public Builder components(List<ComponentSnapshot> components) {
            this.components = components == null ? new ArrayList<>() : new ArrayList<>(components);
            return this;
        }

        public Builder configs(List<ConfigSnapshot> configs) {
            this.configs = configs == null ? new ArrayList<>() : new ArrayList<>(configs);
            return this;
        }

        public Builder schemaVersion(int schemaVersion) {
            this.schemaVersion = schemaVersion;
            return this;
        }

        public Deployment build() {
            Objects.requireNonNull(nodeId, "nodeId");
            return new Deployment(this);
        }
    }
}
