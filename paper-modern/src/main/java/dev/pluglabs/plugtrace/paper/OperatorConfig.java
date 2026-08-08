package dev.pluglabs.plugtrace.paper;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Validates and normalizes operator {@code config.yml} values for load/reload.
 */
public final class OperatorConfig {
    public static final String PRIVACY_HASH_ONLY = "hash-only";

    public final int retentionDeployments;
    public final int rawSamplesPerIssue;
    public final boolean jarRetentionEnabled;
    public final int jarMaxMb;
    public final int jarVersionsPerComponent;
    public final long initialDelaySeconds;
    public final long observationMinutes;
    public final String privacyMode;
    public final boolean metricsEnabled;
    public final boolean webEnabled;
    public final String webBind;
    public final int webPort;
    public final boolean webAllowRemote;
    public final boolean cloudEnabled;
    public final String cloudUploadUrl;
    public final String cloudViewerUrl;
    public final int cloudTtlDays;
    /** Empty = Discord webhook notify off (default). */
    public final String notifyDiscordWebhookUrl;
    public final boolean notifyOnFailing;
    public final boolean notifyOnDegraded;
    public final List<String> warnings;

    private OperatorConfig(
            int retentionDeployments,
            int rawSamplesPerIssue,
            boolean jarRetentionEnabled,
            int jarMaxMb,
            int jarVersionsPerComponent,
            long initialDelaySeconds,
            long observationMinutes,
            String privacyMode,
            boolean metricsEnabled,
            boolean webEnabled,
            String webBind,
            int webPort,
            boolean webAllowRemote,
            boolean cloudEnabled,
            String cloudUploadUrl,
            String cloudViewerUrl,
            int cloudTtlDays,
            String notifyDiscordWebhookUrl,
            boolean notifyOnFailing,
            boolean notifyOnDegraded,
            List<String> warnings
    ) {
        this.retentionDeployments = retentionDeployments;
        this.rawSamplesPerIssue = rawSamplesPerIssue;
        this.jarRetentionEnabled = jarRetentionEnabled;
        this.jarMaxMb = jarMaxMb;
        this.jarVersionsPerComponent = jarVersionsPerComponent;
        this.initialDelaySeconds = initialDelaySeconds;
        this.observationMinutes = observationMinutes;
        this.privacyMode = privacyMode;
        this.metricsEnabled = metricsEnabled;
        this.webEnabled = webEnabled;
        this.webBind = webBind;
        this.webPort = webPort;
        this.webAllowRemote = webAllowRemote;
        this.cloudEnabled = cloudEnabled;
        this.cloudUploadUrl = cloudUploadUrl;
        this.cloudViewerUrl = cloudViewerUrl;
        this.cloudTtlDays = cloudTtlDays;
        this.notifyDiscordWebhookUrl = notifyDiscordWebhookUrl;
        this.notifyOnFailing = notifyOnFailing;
        this.notifyOnDegraded = notifyOnDegraded;
        this.warnings = List.copyOf(warnings);
    }

    public static OperatorConfig from(FileConfiguration config) {
        List<String> warnings = new ArrayList<>();

        int deployments = config.getInt("retention.deployments", 100);
        if (deployments < 1) {
            warnings.add("retention.deployments clamped from " + deployments + " to 1");
            deployments = 1;
        }

        int rawSamples = config.getInt("retention.rawSamplesPerIssue", 3);
        if (rawSamples < 1) {
            warnings.add("retention.rawSamplesPerIssue clamped from " + rawSamples + " to 1");
            rawSamples = 1;
        }

        boolean jarEnabled = config.getBoolean("retention.enabled", true);
        int jarMaxMb = config.getInt("retention.jarMaxMb", 512);
        if (jarMaxMb < 16) {
            warnings.add("retention.jarMaxMb clamped from " + jarMaxMb + " to 16");
            jarMaxMb = 16;
        }
        int jarVersions = config.getInt("retention.jarVersionsPerComponent", 3);
        if (jarVersions < 1) {
            warnings.add("retention.jarVersionsPerComponent clamped from " + jarVersions + " to 1");
            jarVersions = 1;
        }

        long initialDelay = config.getLong("verification.initialDelaySeconds", 30L);
        if (initialDelay < 1L) {
            warnings.add("verification.initialDelaySeconds clamped from " + initialDelay + " to 1");
            initialDelay = 1L;
        }
        long observation = config.getLong("verification.observationMinutes", 15L);
        if (observation < 1L) {
            warnings.add("verification.observationMinutes clamped from " + observation + " to 1");
            observation = 1L;
        }

        String privacy = config.getString("privacy.mode", PRIVACY_HASH_ONLY);
        if (privacy == null || privacy.isBlank()) {
            privacy = PRIVACY_HASH_ONLY;
        }
        privacy = privacy.trim().toLowerCase(Locale.ROOT);
        if (!PRIVACY_HASH_ONLY.equals(privacy) && !"hash-only-config".equals(privacy)) {
            warnings.add("privacy.mode '" + privacy + "' is not implemented; using hash-only "
                    + "(strict/standard/support-detailed are planned)");
            privacy = PRIVACY_HASH_ONLY;
        }
        if ("hash-only-config".equals(privacy)) {
            privacy = PRIVACY_HASH_ONLY;
        }

        boolean metricsEnabled = config.getBoolean("metrics.enabled", true);

        boolean webEnabled = config.getBoolean("web.enabled", true);
        String bind = config.getString("web.bind", "127.0.0.1");
        if (bind == null || bind.isBlank()) {
            warnings.add("web.bind was empty; using 127.0.0.1");
            bind = "127.0.0.1";
        }
        int port = config.getInt("web.port", 9465);
        if (port < 1 || port > 65535) {
            warnings.add("web.port " + port + " out of range; using 9465");
            port = 9465;
        }
        boolean allowRemote = config.getBoolean("web.allowRemote", false);

        boolean cloudEnabled = config.getBoolean("cloud.enabled", true);
        String uploadUrl = config.getString("cloud.uploadUrl", "https://plugtrace.dev/");
        if (uploadUrl == null || uploadUrl.isBlank()) {
            warnings.add("cloud.uploadUrl was empty; using https://plugtrace.dev/");
            uploadUrl = "https://plugtrace.dev/";
        }
        String viewerUrl = config.getString("cloud.viewerUrl", "https://plugtrace.dev/");
        if (viewerUrl == null || viewerUrl.isBlank()) {
            warnings.add("cloud.viewerUrl was empty; using https://plugtrace.dev/");
            viewerUrl = "https://plugtrace.dev/";
        }
        int ttlDays = config.getInt("cloud.ttlDays", 14);
        if (ttlDays < 1) {
            warnings.add("cloud.ttlDays clamped from " + ttlDays + " to 1");
            ttlDays = 1;
        }
        if (ttlDays > 90) {
            warnings.add("cloud.ttlDays clamped from " + ttlDays + " to 90");
            ttlDays = 90;
        }

        String webhook = config.getString("notify.discordWebhookUrl", "");
        if (webhook == null) {
            webhook = "";
        }
        webhook = webhook.trim();
        if (!webhook.isEmpty() && !webhook.startsWith("https://")) {
            warnings.add("notify.discordWebhookUrl ignored — must be https URL (or empty to disable)");
            webhook = "";
        }
        boolean onFailing = config.getBoolean("notify.onFailing", true);
        boolean onDegraded = config.getBoolean("notify.onDegraded", false);

        return new OperatorConfig(
                deployments, rawSamples, jarEnabled, jarMaxMb, jarVersions,
                initialDelay, observation, privacy, metricsEnabled,
                webEnabled, bind, port, allowRemote,
                cloudEnabled, uploadUrl.trim(), viewerUrl.trim(), ttlDays,
                webhook, onFailing, onDegraded, warnings
        );
    }

    public Map<String, Object> toMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("retention.deployments", retentionDeployments);
        out.put("retention.rawSamplesPerIssue", rawSamplesPerIssue);
        out.put("retention.enabled", jarRetentionEnabled);
        out.put("retention.jarMaxMb", jarMaxMb);
        out.put("retention.jarVersionsPerComponent", jarVersionsPerComponent);
        out.put("verification.initialDelaySeconds", initialDelaySeconds);
        out.put("verification.observationMinutes", observationMinutes);
        out.put("privacy.mode", privacyMode);
        out.put("metrics.enabled", metricsEnabled);
        out.put("web.enabled", webEnabled);
        out.put("web.bind", webBind);
        out.put("web.port", webPort);
        out.put("web.allowRemote", webAllowRemote);
        out.put("cloud.enabled", cloudEnabled);
        out.put("cloud.uploadUrl", cloudUploadUrl);
        out.put("cloud.viewerUrl", cloudViewerUrl);
        out.put("cloud.ttlDays", cloudTtlDays);
        out.put("notify.discordWebhookConfigured",
                notifyDiscordWebhookUrl != null && !notifyDiscordWebhookUrl.isBlank());
        out.put("notify.onFailing", notifyOnFailing);
        out.put("notify.onDegraded", notifyOnDegraded);
        out.put("thresholds", Map.of(
                "configResetStructuralAndBytes", "40%",
                "msptMedianDeltaMs", 10,
                "msptMedianDeltaPercent", 50,
                "note", "Canonical hardcoded thresholds — not config.yml keys"
        ));
        return out;
    }

    /** Report schema privacyMode value. */
    public String reportPrivacyMode() {
        return "hash-only".equals(privacyMode) ? "hash-only-config" : privacyMode;
    }
}
