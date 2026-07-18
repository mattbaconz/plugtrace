package dev.pluglabs.plugtrace.report;

import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.Incident;
import dev.pluglabs.plugtrace.domain.DeploymentVerification;
import dev.pluglabs.plugtrace.domain.Suspect;

import java.util.List;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ReportRequest {
    private final Deployment current;
    private final Deployment baseline;
    private final List<Change> changes;
    private final List<Issue> issues;
    private final List<Suspect> suspects;
    private final List<Annotation> annotations;
    private final String baselineDescription;
    private final Map<String, Object> spark;
    private final Map<String, Object> scope;
    private final Map<String, Object> platform;
    private final Map<String, Object> release;
    private final Map<String, Object> pluginFields;
    private final List<String> previewSections;
    private final DeploymentVerification verification;
    private final List<Incident> incidents;
    private final String privacyMode;

    public ReportRequest(
            Deployment current,
            Deployment baseline,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects,
            List<Annotation> annotations,
            String baselineDescription,
            Map<String, Object> spark,
            Map<String, Object> scope,
            Map<String, Object> platform,
            List<String> previewSections
    ) {
        this(current, baseline, changes, issues, suspects, annotations, baselineDescription,
                spark, scope, platform, null, null, previewSections);
    }

    public ReportRequest(
            Deployment current,
            Deployment baseline,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects,
            List<Annotation> annotations,
            String baselineDescription,
            Map<String, Object> spark,
            Map<String, Object> scope,
            Map<String, Object> platform,
            Map<String, Object> release,
            List<String> previewSections
    ) {
        this(current, baseline, changes, issues, suspects, annotations, baselineDescription,
                spark, scope, platform, release, null, previewSections);
    }

    public ReportRequest(
            Deployment current,
            Deployment baseline,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects,
            List<Annotation> annotations,
            String baselineDescription,
            Map<String, Object> spark,
            Map<String, Object> scope,
            Map<String, Object> platform,
            Map<String, Object> release,
            Map<String, Object> pluginFields,
            List<String> previewSections
    ) {
        this(current, baseline, changes, issues, suspects, annotations, baselineDescription, spark, scope,
                platform, release, pluginFields, previewSections, null, List.of());
    }

    public ReportRequest(
            Deployment current,
            Deployment baseline,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects,
            List<Annotation> annotations,
            String baselineDescription,
            Map<String, Object> spark,
            Map<String, Object> scope,
            Map<String, Object> platform,
            Map<String, Object> release,
            Map<String, Object> pluginFields,
            List<String> previewSections,
            DeploymentVerification verification,
            List<Incident> incidents
    ) {
        this(current, baseline, changes, issues, suspects, annotations, baselineDescription, spark, scope,
                platform, release, pluginFields, previewSections, verification, incidents, null);
    }

    public ReportRequest(
            Deployment current,
            Deployment baseline,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects,
            List<Annotation> annotations,
            String baselineDescription,
            Map<String, Object> spark,
            Map<String, Object> scope,
            Map<String, Object> platform,
            Map<String, Object> release,
            Map<String, Object> pluginFields,
            List<String> previewSections,
            DeploymentVerification verification,
            List<Incident> incidents,
            String privacyMode
    ) {
        this.current = current;
        this.baseline = baseline;
        this.changes = changes == null ? List.of() : List.copyOf(changes);
        this.issues = issues == null ? List.of() : List.copyOf(issues);
        this.suspects = suspects == null ? List.of() : List.copyOf(suspects);
        this.annotations = annotations == null ? List.of() : List.copyOf(annotations);
        this.baselineDescription = baselineDescription == null ? "" : baselineDescription;
        this.spark = immutableMap(spark == null ? Map.of("detected", false) : spark);
        this.scope = immutableMap(scope == null ? Map.of("type", "deployment") : scope);
        this.platform = immutableMap(platform == null ? Map.of() : platform);
        this.release = immutableMap(release == null ? Map.of() : release);
        this.pluginFields = immutableMap(pluginFields == null ? Map.of() : pluginFields);
        this.previewSections = previewSections == null
                ? List.of("executiveSummary", "deployment", "baseline", "changes", "issues", "suspects",
                "verification", "incidents", "annotations", "spark", "release", "pluginFields", "redactionWarnings")
                : List.copyOf(previewSections);
        this.verification = verification;
        this.incidents = incidents == null ? List.of() : List.copyOf(incidents);
        this.privacyMode = privacyMode == null || privacyMode.isBlank() ? "hash-only-config" : privacyMode;
    }

    private static Map<String, Object> immutableMap(Map<String, Object> source) {
        return Collections.unmodifiableMap(new LinkedHashMap<>(source));
    }

    public Deployment current() {
        return current;
    }

    public Deployment baseline() {
        return baseline;
    }

    public List<Change> changes() {
        return changes;
    }

    public List<Issue> issues() {
        return issues;
    }

    public List<Suspect> suspects() {
        return suspects;
    }

    public List<Annotation> annotations() {
        return annotations;
    }

    public String baselineDescription() {
        return baselineDescription;
    }

    public Map<String, Object> spark() {
        return spark;
    }

    public Map<String, Object> scope() {
        return scope;
    }

    public Map<String, Object> platform() {
        return platform;
    }

    public Map<String, Object> release() {
        return release;
    }

    public Map<String, Object> pluginFields() {
        return pluginFields;
    }

    public List<String> previewSections() {
        return previewSections;
    }

    public DeploymentVerification verification() { return verification; }

    public List<Incident> incidents() { return incidents; }

    public String privacyMode() {
        return privacyMode;
    }
}
