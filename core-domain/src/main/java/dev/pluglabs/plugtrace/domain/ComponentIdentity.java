package dev.pluglabs.plugtrace.domain;

import java.util.List;
import java.util.Objects;

public final class ComponentIdentity {
    private final ComponentType type;
    private final String normalizedName;
    private final String declaredVersion;
    private final String binaryHash;
    private final List<String> authors;
    private final List<String> dependencies;
    private final List<String> softDependencies;
    private final String mainClass;
    private final String apiVersion;

    public ComponentIdentity(
            ComponentType type,
            String normalizedName,
            String declaredVersion,
            String binaryHash,
            List<String> authors,
            List<String> dependencies,
            List<String> softDependencies,
            String mainClass,
            String apiVersion
    ) {
        this.type = Objects.requireNonNull(type, "type");
        this.normalizedName = Objects.requireNonNull(normalizedName, "normalizedName");
        this.declaredVersion = declaredVersion == null ? "unknown" : declaredVersion;
        this.binaryHash = binaryHash == null ? "" : binaryHash;
        this.authors = authors == null ? List.of() : List.copyOf(authors);
        this.dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
        this.softDependencies = softDependencies == null ? List.of() : List.copyOf(softDependencies);
        this.mainClass = mainClass;
        this.apiVersion = apiVersion;
    }

    public ComponentType type() {
        return type;
    }

    public String normalizedName() {
        return normalizedName;
    }

    public String declaredVersion() {
        return declaredVersion;
    }

    public String binaryHash() {
        return binaryHash;
    }

    public List<String> authors() {
        return authors;
    }

    public List<String> dependencies() {
        return dependencies;
    }

    public List<String> softDependencies() {
        return softDependencies;
    }

    public String mainClass() {
        return mainClass;
    }

    public String apiVersion() {
        return apiVersion;
    }

    public String identityKey() {
        return type.name() + ":" + normalizedName.toLowerCase();
    }
}
