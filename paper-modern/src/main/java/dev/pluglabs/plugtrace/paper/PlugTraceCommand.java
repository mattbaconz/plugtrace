package dev.pluglabs.plugtrace.paper;

import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.PlatformInfo;
import dev.pluglabs.plugtrace.domain.RestorePlan;
import dev.pluglabs.plugtrace.domain.Suspect;
import dev.pluglabs.plugtrace.report.ReportService;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

public final class PlugTraceCommand implements CommandExecutor, TabCompleter {
    private final PlugTraceService service;
    private final PlugTracePlugin plugin;
    private LocalWebServer web;

    public PlugTraceCommand(PlugTraceService service, PlugTracePlugin plugin) {
        this.service = service;
        this.plugin = plugin;
        this.web = plugin.web();
    }

    void setWeb(LocalWebServer web) {
        this.web = web;
    }

    @Override
    public boolean onCommand(
            CommandSender sender,
            Command command,
            String label,
            String[] args
    ) {
        if (args.length == 0) {
            out(sender, "Usage: /plugtrace <status|checkpoint|verify|expected|deployments|diff|issues|incidents|report|web|restore|reload|selfcheck>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String required = permissionFor(sub);
        if (!sender.hasPermission("plugtrace.admin") && !sender.hasPermission(required)) {
            PlugTraceMessages.fail(sender, "You lack permission " + required);
            return true;
        }
        return switch (sub) {
            case "status" -> status(sender);
            case "checkpoint" -> checkpoint(sender, args);
            case "verify" -> verify(sender, args);
            case "expected" -> expected(sender, args);
            case "deployments" -> deployments(sender);
            case "diff" -> diff(sender);
            case "issues" -> issues(sender);
            case "incidents" -> incidents(sender, args);
            case "suspect" -> suspect(sender, args);
            case "issue" -> issue(sender, args);
            case "report" -> report(sender, args);
            case "mark" -> mark(sender, args);
            case "annotate" -> annotate(sender, args);
            case "spark" -> spark(sender, args);
            case "compatibility" -> compatibility(sender);
            case "selfcheck" -> selfcheck(sender);
            case "restore" -> restore(sender, args);
            case "web" -> web(sender, args);
            case "reload" -> reload(sender);
            default -> {
                PlugTraceMessages.warn(sender, "Unknown subcommand.");
                yield true;
            }
        };
    }

    private boolean checkpoint(CommandSender sender, String[] args) {
        String name = args.length > 1 ? Arrays.stream(args).skip(1).collect(Collectors.joining(" ")) : "checkpoint";
        final dev.pluglabs.plugtrace.domain.Checkpoint checkpoint;
        try {
            checkpoint = service.createCheckpoint(name, sender.getName());
        } catch (IllegalStateException exception) {
            out(sender, "Checkpoint not created: " + exception.getMessage());
            return true;
        }
        out(sender, "Checkpoint " + checkpoint.id() + " references deployment #"
                + service.currentDeployment().localSequence() + ". It is not a full backup.");
        return true;
    }

    private boolean verify(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("run")) {
            service.requestVerification(false);
            out(sender, "Verification started. Use /plugtrace verify status for the result.");
            return true;
        }
        var result = service.currentVerification();
        out(sender, result == null ? "Verification has not run yet." :
                "Verification " + result.id() + ": " + result.health() + " checks=" + result.checks().size());
        return true;
    }

    private boolean expected(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("capture")) {
            List<String> plugins = Arrays.stream(plugin.getServer().getPluginManager().getPlugins())
                    .filter(org.bukkit.plugin.Plugin::isEnabled)
                    .map(org.bukkit.plugin.Plugin::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> commands = livePluginCommands();
            List<String> worlds = plugin.getServer().getWorlds().stream()
                    .map(org.bukkit.World::getName)
                    .sorted(String.CASE_INSENSITIVE_ORDER)
                    .toList();
            List<String> services = liveServices();
            var state = service.captureExpectedState(plugins, commands, worlds, services);
            out(sender, "Captured expected state " + state.id() + " from deployment #"
                    + service.currentDeployment().localSequence() + ".");
            out(sender, "- plugins=" + plugins.size()
                    + " commands=" + commands.size()
                    + " worlds=" + worlds.size()
                    + " services=" + services.size());
            return true;
        }
        service.expectedState().ifPresent(state -> out(sender, "Captured expected state: " + state.id()
                + " from deployment " + state.sourceDeploymentId()));
        out(sender, "Expected plugins: " + plugin.getConfig().getStringList("expected.plugins"));
        out(sender, "Expected commands: " + plugin.getConfig().getStringList("expected.commands"));
        out(sender, "Expected worlds: " + plugin.getConfig().getStringList("expected.worlds"));
        out(sender, "Expected services: " + plugin.getConfig().getStringList("expected.services"));
        return true;
    }

    private List<String> livePluginCommands() {
        return Arrays.stream(plugin.getServer().getPluginManager().getPlugins())
                .filter(org.bukkit.plugin.Plugin::isEnabled)
                .flatMap(p -> p.getDescription().getCommands().keySet().stream())
                .distinct()
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private List<String> liveServices() {
        return plugin.getServer().getServicesManager().getKnownServices().stream()
                .map(Class::getName)
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .toList();
    }

    private boolean reload(CommandSender sender) {
        out(sender, "Reloading PlugTrace config.yml…");
        for (String line : plugin.reloadOperatorConfig()) {
            out(sender, "- " + line);
        }
        out(sender, "Done. Run /plugtrace selfcheck to confirm effective values.");
        return true;
    }

    private boolean incidents(CommandSender sender, String[] args) {
        var incidents = service.currentIncidents();
        if (args.length > 1) {
            var selected = incidents.stream().filter(incident -> incident.id().equalsIgnoreCase(args[1])).findFirst();
            if (selected.isEmpty()) {
                out(sender, "Incident not found in the current deployment.");
                return true;
            }
            var incident = selected.get();
            out(sender, incident.status() + " " + incident.id() + " - " + incident.summary());
            out(sender, "Failed checks: " + incident.failedCheckIds());
            out(sender, "Issue fingerprints: " + incident.issueFingerprints());
            return true;
        }
        if (incidents.isEmpty()) out(sender, "No incidents for the current deployment.");
        incidents.forEach(incident -> out(sender, incident.status() + " " + incident.id() + " " + incident.summary()
                + " checks=" + incident.failedCheckIds().size() + " issues=" + incident.issueFingerprints().size()));
        return true;
    }

    private boolean web(CommandSender sender, String[] args) {
        if (web == null) { out(sender, "PlugTrace Web is disabled."); return true; }
        if (args.length < 2 || args[1].equalsIgnoreCase("status")) {
            out(sender, "PlugTrace Web: " + web.address() + " (token required)");
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender)) {
            out(sender, "Web token management is console-only.");
            return true;
        }
        try {
            if (args[1].equalsIgnoreCase("token") && args.length >= 4 && args[2].equalsIgnoreCase("create")) {
                String name = args[3];
                WebTokenStore.Scope scope = args.length >= 5 && args[4].equalsIgnoreCase("read")
                        ? WebTokenStore.Scope.READ : WebTokenStore.Scope.ADMIN;
                out(sender, "Token " + name + " (shown once): " + web.createToken(name, scope));
                return true;
            }
            if (args[1].equalsIgnoreCase("token") && args.length >= 4 && args[2].equalsIgnoreCase("revoke")) {
                out(sender, web.revokeToken(args[3]) ? "Token revoked." : "Token not found.");
                return true;
            }
        } catch (Exception e) {
            out(sender, "Web token operation failed: " + e.getMessage());
            return true;
        }
        out(sender, "Usage: /plugtrace web <status|token create <name> [read|admin]|token revoke <name>>");
        return true;
    }

    private static String permissionFor(String sub) {
        return switch (sub) {
            case "checkpoint" -> "plugtrace.checkpoint";
            case "verify", "expected" -> "plugtrace.verify";
            case "report" -> "plugtrace.report";
            case "mark", "annotate" -> "plugtrace.mark";
            case "restore" -> "plugtrace.restore";
            case "web", "reload" -> "plugtrace.web.admin";
            default -> "plugtrace.view";
        };
    }

    private boolean status(CommandSender sender) {
        Deployment current = service.currentDeployment();
        PlatformInfo platform = service.platformInfo();
        Map<String, Object> ritual = service.ritualStatus();
        PlugTraceMessages.title(sender, "Status");
        PlugTraceMessages.send(sender, PlugTraceMessages.healthMini(current.health())
                + " <dark_gray>-</dark_gray> <gray>deployment</gray> <aqua>#"
                + current.localSequence() + "</aqua> <dark_gray>("
                + PlugTraceMessages.escape(current.id()) + ")</dark_gray>");
        PlugTraceMessages.row(sender, "Baseline", service.baselineDescription());
        PlugTraceMessages.row(sender, "Changes", String.valueOf(service.currentChanges().size()));
        PlugTraceMessages.row(sender, "Issues", String.valueOf(service.currentIssues().size()));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> topChanges = (List<Map<String, Object>>) ritual.get("topChanges");
        if (topChanges != null && !topChanges.isEmpty()) {
            PlugTraceMessages.send(sender, "<gradient:#22d3ee:#2dd4bf>Top changes</gradient>");
            for (Map<String, Object> row : topChanges) {
                boolean churn = Boolean.TRUE.equals(row.get("knownChurn"));
                PlugTraceMessages.send(sender, "<dark_gray>-</dark_gray> <aqua>"
                        + PlugTraceMessages.escape(String.valueOf(row.get("type")))
                        + "</aqua> <white>"
                        + PlugTraceMessages.escape(String.valueOf(row.get("component")))
                        + "</white>"
                        + (churn ? " <dark_gray>[known churn]</dark_gray>" : "")
                        + (row.get("explanation") == null || String.valueOf(row.get("explanation")).isBlank()
                        ? ""
                        : " <dark_gray>-</dark_gray> <gray>"
                        + PlugTraceMessages.escape(String.valueOf(row.get("explanation")))
                        + "</gray>"));
            }
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> suspect = (Map<String, Object>) ritual.get("strongestSuspect");
        if (suspect == null) {
            PlugTraceMessages.row(sender, "Strongest suspect", "Unknown");
        } else {
            boolean churn = Boolean.TRUE.equals(suspect.get("knownChurn"));
            PlugTraceMessages.row(sender, "Strongest suspect",
                    suspect.get("component") + " [" + suspect.get("band") + "]"
                            + (churn ? " (known churn)" : ""));
            PlugTraceMessages.send(sender, "<dark_gray>  </dark_gray><gray>"
                    + PlugTraceMessages.escape(String.valueOf(suspect.get("summary"))) + "</gray>");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> noise = (Map<String, Object>) ritual.get("noiseContext");
        if (noise != null) {
            long churnChanges = ((Number) noise.getOrDefault("knownChurnChangeCount", 0)).longValue();
            long churnIssues = ((Number) noise.getOrDefault("knownChurnIssueCount", 0)).longValue();
            if (churnChanges > 0 || churnIssues > 0 || Boolean.TRUE.equals(noise.get("plugDevPresent"))) {
                PlugTraceMessages.row(sender, "Known churn",
                        churnChanges + " changes / " + churnIssues + " issues"
                                + (Boolean.TRUE.equals(noise.get("plugDevPresent")) ? " (PlugDev present)" : ""));
                if (noise.get("hint") != null) {
                    PlugTraceMessages.send(sender, "<dark_gray>  </dark_gray><gray>"
                            + PlugTraceMessages.escape(String.valueOf(noise.get("hint"))) + "</gray>");
                }
            }
        }
        @SuppressWarnings("unchecked")
        List<String> nextCommands = (List<String>) ritual.get("nextCommands");
        PlugTraceMessages.send(sender, "<white>Next:</white> <aqua>"
                + PlugTraceMessages.escape(String.valueOf(ritual.get("nextCommand"))) + "</aqua>");
        if (nextCommands != null && nextCommands.size() > 1) {
            for (int i = 1; i < Math.min(3, nextCommands.size()); i++) {
                PlugTraceMessages.send(sender, "<dark_gray>  </dark_gray><aqua>"
                        + PlugTraceMessages.escape(nextCommands.get(i)) + "</aqua>");
            }
        }
        PlugTraceMessages.row(sender, "Platform", platform.forkFamily() + " / " + platform.supportTier());
        PlugTraceMessages.row(sender, "Artifact", platform.artifact());
        if (service.plugDevIdentity() != null) {
            var id = service.plugDevIdentity();
            PlugTraceMessages.row(sender, "PlugDev",
                    id.projectName() + " @" + id.shortCommit() + (id.gitDirty() ? " dirty" : ""));
        } else {
            PlugTraceMessages.row(sender, "PlugDev", "absent");
        }
        PlugTraceMessages.row(sender, "Spark",
                service.sparkDetected() ? service.sparkVersion() : "absent");
        PlugTraceMessages.row(sender, "Storage", "local SQLite (nothing uploaded)");
        return true;
    }

    private boolean deployments(CommandSender sender) {
        for (Deployment deployment : service.listDeployments(15)) {
            out(sender, "#" + deployment.localSequence()
                    + " " + deployment.health()
                    + " " + deployment.startedAt()
                    + " plugins=" + deployment.components().size());
        }
        return true;
    }

    private boolean diff(CommandSender sender) {
        List<Change> changes = service.currentChanges();
        if (changes.isEmpty()) {
            out(sender, "No typed changes versus baseline.");
            return true;
        }
        for (Change change : changes) {
            out(sender, change.type() + " " + change.componentKey() + " - " + change.explanation());
        }
        return true;
    }

    private boolean issues(CommandSender sender) {
        List<Issue> issues = service.currentIssues();
        if (issues.isEmpty()) {
            out(sender, "No issues recorded for the current deployment.");
            return true;
        }
        for (Issue issue : issues) {
            out(sender, issue.regressionClass() + "/" + issue.status()
                    + " ×" + issue.occurrenceCount()
                    + " fp=" + issue.fingerprint().substring(0, Math.min(8, issue.fingerprint().length()))
                    + " - " + issue.normalizedMessage());
        }
        return true;
    }

    private boolean suspect(CommandSender sender, String[] args) {
        List<Suspect> suspects = service.currentSuspects();
        if (suspects.isEmpty()) {
            out(sender, "No suspects. Unknown is valid.");
            return true;
        }
        int requestedRank = 1;
        if (args.length >= 2) {
            try {
                requestedRank = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                out(sender, "Usage: /plugtrace suspect [rank]");
                return true;
            }
        }
        final int rank = requestedRank;
        Suspect match = suspects.stream().filter(s -> s.rank() == rank).findFirst().orElse(null);
        if (match == null) {
            out(sender, "No suspect at rank " + rank);
            return true;
        }
        out(sender, "Suspect #" + match.rank() + " " + match.componentKey() + " [" + match.band() + "]");
        out(sender, match.changeSummary());
        for (var evidence : match.supporting()) {
            out(sender, "+ [" + evidence.source() + "] " + evidence.explanation());
        }
        for (var evidence : match.contradictions()) {
            out(sender, "- [" + evidence.source() + "] " + evidence.explanation());
        }
        return true;
    }

    private boolean issue(CommandSender sender, String[] args) {
        if (args.length < 2) {
            out(sender, "Usage: /plugtrace issue <fingerprint-prefix>");
            return true;
        }
        String prefix = args[1].toLowerCase(Locale.ROOT);
        Issue match = service.currentIssues().stream()
                .filter(i -> i.fingerprint().toLowerCase(Locale.ROOT).startsWith(prefix))
                .findFirst()
                .orElse(null);
        if (match == null) {
            out(sender, "No issue with fingerprint prefix " + args[1]);
            return true;
        }
        out(sender, "Issue " + match.fingerprint());
        out(sender, "Status: " + match.status() + " / " + match.regressionClass());
        out(sender, "Message: " + match.normalizedMessage());
        out(sender, "Ownership: " + match.ownershipCandidates());
        out(sender, "Count: " + match.occurrenceCount());
        if (match.sampleStack() != null && !match.sampleStack().isBlank()) {
            String[] lines = match.sampleStack().split("\\R");
            for (int i = 0; i < Math.min(8, lines.length); i++) {
                out(sender, lines[i]);
            }
        }
        return true;
    }

    private boolean report(CommandSender sender, String[] args) {
        if (args.length >= 2 && args[1].equalsIgnoreCase("preview")) {
            out(sender, "Support report preview (share like a spark link when needed):");
            out(sender, "Sections: executiveSummary, deployment, baseline, changes, issues, suspects, annotations, spark, redactionWarnings");
            out(sender, "Nothing uploaded. Hash-only configs. Secrets redacted in samples.");
            out(sender, "Pasteable share (explicit only): /plugtrace report upload → plugtrace.dev URL with #k=…");
            if (service.sparkDetected()) {
                out(sender, "Lag? Attach spark too: /plugtrace spark link <profile-url>");
            }
            return true;
        }
        if (args.length >= 2 && args[1].equalsIgnoreCase("upload")) {
            return reportUpload(sender);
        }
        ReportService.ReportArtifacts artifacts;
        String kind = "full";
        if (args.length >= 3 && args[1].equalsIgnoreCase("plugin")) {
            artifacts = service.generatePluginScopedReport(args[2]);
            kind = "plugin:" + args[2];
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("discord")) {
            artifacts = service.generateReport();
            kind = "discord";
            out(sender, artifacts.discord());
        } else if (args.length >= 2 && args[1].equalsIgnoreCase("github")) {
            artifacts = service.generateReport();
            kind = "github";
        } else {
            artifacts = service.generateReport();
        }
        out(sender, "Report written (schema " + artifacts.schemaVersion() + ", " + kind + ").");
        out(sender, "Artifact hash: " + artifacts.artifactHash());
        out(sender, "Preview: " + String.join(", ", artifacts.previewSections()));
        out(sender, "Files: plugins/PlugTrace/reports/deployment-"
                + service.currentDeployment().localSequence() + ".{json,md,html,discord.txt,github.md}");
        out(sender, "Share like a spark link (optional): /plugtrace report upload");
        return true;
    }

    private boolean reportUpload(CommandSender sender) {
        OperatorConfig cfg = service.operatorConfig();
        if (cfg == null || !cfg.cloudEnabled) {
            out(sender, "Hosted upload disabled (cloud.enabled=false). Local report files still work.");
            return true;
        }
        if (!(sender instanceof ConsoleCommandSender) && !sender.hasPermission("plugtrace.admin")) {
            out(sender, "Hosted upload requires plugtrace.admin (or console).");
            return true;
        }
        out(sender, "Generating redacted report and uploading ciphertext (key stays in URL fragment)…");
        out(sender, "This is the spark-shaped share path - paste the full URL into Discord/GitHub.");
        ReportService.ReportArtifacts artifacts;
        try {
            artifacts = service.generateReport();
        } catch (RuntimeException e) {
            out(sender, "Local report generation failed; nothing uploaded: " + e.getMessage());
            return true;
        }
        out(sender, "Local files written under plugins/PlugTrace/reports/ (upload is optional).");
        try {
            HostedReportClient.UploadResult result = HostedReportClient.upload(
                    cfg.cloudUploadUrl,
                    cfg.cloudViewerUrl,
                    artifacts.json(),
                    artifacts.schemaVersion(),
                    cfg.cloudTtlDays
            );
            service.recordHostedReport(result.id(), result.shareUrl(), result.expiresAt(), result.deleteToken());
            out(sender, "Uploaded. Share URL (copy full link including #k=… - like a spark profile):");
            out(sender, result.shareUrl());
            if (result.expiresAt() != null) {
                out(sender, "Expires: " + result.expiresAt());
            }
            if (service.sparkDetected()) {
                out(sender, "If this incident includes lag: attach spark via /plugtrace spark link <url> and mention both links.");
            }
            out(sender, "Delete token (keep private): " + result.deleteToken());
            out(sender, "Privacy: https://plugtrace.dev/privacy");
            return true;
        } catch (Exception e) {
            out(sender, "Hosted upload failed; local report files remain: " + e.getMessage());
            plugin.getLogger().warning("Hosted report upload failed: " + e.getMessage());
            return true;
        }
    }

    private boolean mark(CommandSender sender, String[] args) {
        if (args.length < 2) {
            out(sender, "Usage: /plugtrace mark <healthy|degraded|broken> [note...]");
            return true;
        }
        DeploymentHealth health = switch (args[1].toLowerCase(Locale.ROOT)) {
            case "healthy" -> DeploymentHealth.HEALTHY;
            case "degraded" -> DeploymentHealth.DEGRADED;
            case "broken", "failing" -> DeploymentHealth.FAILING;
            default -> null;
        };
        if (health == null) {
            out(sender, "Health must be healthy|degraded|broken");
            return true;
        }
        String note = args.length > 2
                ? Arrays.stream(args).skip(2).collect(Collectors.joining(" "))
                : "Marked via /plugtrace mark";
        service.markHealth(health, note);
        out(sender, "Deployment #" + service.currentDeployment().localSequence() + " marked " + health);
        return true;
    }

    private boolean annotate(CommandSender sender, String[] args) {
        if (args.length < 3) {
            out(sender, "Usage: /plugtrace annotate <ops|migration|traffic|host|season|ddos|other> <text...>");
            return true;
        }
        String category = args[1];
        String text = Arrays.stream(args).skip(2).collect(Collectors.joining(" "));
        Annotation annotation = service.annotate(category, text, sender.getName(), null);
        out(sender, "Annotation saved (" + annotation.category() + "): " + annotation.text());
        return true;
    }

    private boolean spark(CommandSender sender, String[] args) {
        if (args.length >= 3 && args[1].equalsIgnoreCase("link")) {
            service.setSparkLink(args[2]);
            out(sender, "Spark profile URL linked for current deployment.");
            return true;
        }
        out(sender, "Spark detected: " + service.sparkDetected()
                + (service.sparkVersion() == null ? "" : " (" + service.sparkVersion() + ")"));
        out(sender, "Profile URL: " + (service.sparkProfileUrl() == null ? "(none)" : service.sparkProfileUrl()));
        out(sender, "Usage: /plugtrace spark link <url>");
        return true;
    }

    private boolean compatibility(CommandSender sender) {
        PlatformInfo platform = service.platformInfo();
        out(sender, "PlugTrace compatibility");
        out(sender, "- Running artifact: " + service.artifactId());
        out(sender, "- Labeled artifact: " + platform.artifact());
        out(sender, "- Detected fork: " + platform.forkFamily());
        out(sender, "- Support tier: " + platform.supportTier());
        out(sender, "- Java: " + service.currentDeployment().javaVendor() + " "
                + service.currentDeployment().javaVersion());
        out(sender, "- Minecraft: " + service.currentDeployment().minecraftVersion());
        out(sender, "- Capabilities: deployment-snapshot, diff, fingerprint, report-html, annotations, spark-soft-link");
        if (service.plugDevIdentity() != null) {
            out(sender, "- PlugDev commit: " + service.plugDevIdentity().gitCommit());
        }
        if (platform.migrateHint() != null) {
            out(sender, "- Migrate: " + platform.migrateHint());
        } else if ("folia".equals(platform.forkFamily())) {
            out(sender, "- Folia: correct artifact; live soak and certification are still pending");
        } else {
            out(sender, "- Folia: use PlugTrace-folia when running Folia");
        }
        return true;
    }

    private boolean selfcheck(CommandSender sender) {
        PlugTraceMessages.title(sender, "Selfcheck");
        for (String line : service.selfcheck()) {
            PlugTraceMessages.send(sender, "<dark_gray>-</dark_gray> <gray>"
                    + PlugTraceMessages.escape(line) + "</gray>");
        }
        return true;
    }

    private static void out(CommandSender sender, String plain) {
        PlugTraceMessages.plain(sender, plain);
    }

    private boolean restore(CommandSender sender, String[] args) {
        String action = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "preview";
        try {
            return switch (action) {
                case "preview" -> {
                    RestorePlan plan = service.restorePreview();
                    out(sender, "Restore preview → baseline: " + service.baselineDescription());
                    out(sender, "- Actions: " + plan.actions().size() + "  status=" + plan.status());
                    for (RestorePlan.RestoreAction a : plan.actions()) {
                        out(sender, "  " + a.kind() + " " + a.componentKey()
                                + " " + shortHash(a.fromHash()) + "→" + shortHash(a.toHash()));
                    }
                    for (String warning : plan.warnings()) {
                        out(sender, "! " + warning);
                    }
                    out(sender, "Risk: originals will be copied to *.plugtrace-original before replace.");
                    out(sender, "Next: /plugtrace restore stage confirm  → stop → offline finalize → start → verify/complete");
                    yield true;
                }
                case "stage" -> {
                    boolean confirmed = args.length >= 3 && (
                            "confirm".equalsIgnoreCase(args[2]) || "--confirm".equalsIgnoreCase(args[2]));
                    RestorePlan plan = service.restoreStage(confirmed);
                    for (String warning : plan.warnings()) {
                        out(sender, "! " + warning);
                    }
                    out(sender, "Restore STAGED (" + plan.id() + "). Originals kept as *.plugtrace-original.");
                    out(sender, "1) Stop the server cleanly");
                    out(sender, "2) Offline finalize (supported): java -jar core-domain-*.jar <plugins/PlugTrace> "
                            + plan.id());
                    out(sender, "   or: ./gradlew :core-domain:finalizeRestore -PplugtraceData=<plugins/PlugTrace>");
                    out(sender, "3) Start → /plugtrace restore verify → /plugtrace restore complete");
                    out(sender, "Abort anytime: /plugtrace restore abort");
                    yield true;
                }
                case "finalize" -> {
                    try {
                        service.restoreFinalize();
                    } catch (IllegalStateException refused) {
                        out(sender, refused.getMessage());
                    }
                    yield true;
                }
                case "abort" -> {
                    RestorePlan plan = service.restoreAbort();
                    out(sender, "Restore " + plan.status() + " - originals restored where backups existed.");
                    yield true;
                }
                case "verify" -> {
                    for (String line : service.restoreVerify()) {
                        out(sender, "- " + line);
                    }
                    out(sender, "If offline restore applied: /plugtrace restore complete then /plugtrace mark healthy when stable.");
                    yield true;
                }
                case "complete" -> {
                    RestorePlan plan = service.restoreComplete();
                    out(sender, "Restore complete"
                            + (plan != null ? " (" + plan.id() + ")" : "")
                            + " - tagged restored-from-baseline.");
                    out(sender, "When stable: /plugtrace mark healthy");
                    yield true;
                }
                default -> {
                    out(sender, "Usage: /plugtrace restore <preview|stage|finalize|abort|verify|complete>");
                    yield true;
                }
            };
        } catch (Exception e) {
            out(sender, "Restore failed: " + e.getMessage());
            return true;
        }
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.length() < 8) {
            return String.valueOf(hash);
        }
        return hash.substring(0, 8);
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender,
            Command command,
            String alias,
            String[] args
    ) {
        if (args.length == 1) {
            return filter(List.of(
                    "status", "deployments", "diff", "issues", "suspect", "issue",
                    "checkpoint", "verify", "expected", "incidents", "report", "mark", "annotate",
                    "spark", "compatibility", "selfcheck", "restore", "web", "reload"
            ), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("mark")) {
            return filter(List.of("healthy", "degraded", "broken"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("annotate")) {
            return filter(List.of("ops", "migration", "traffic", "host", "season", "ddos", "other"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("report")) {
            return filter(List.of("preview", "upload", "plugin", "discord", "github"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("spark")) {
            return filter(List.of("link"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("restore")) {
            return filter(List.of("preview", "stage", "finalize", "abort", "verify", "complete"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("verify")) return filter(List.of("status", "run"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("expected")) return filter(List.of("show", "capture"), args[1]);
        if (args.length == 2 && args[0].equalsIgnoreCase("web")) return filter(List.of("status", "token"), args[1]);
        if (args.length == 3 && args[0].equalsIgnoreCase("web") && args[1].equalsIgnoreCase("token"))
            return filter(List.of("create", "revoke"), args[2]);
        if (args.length == 3 && args[0].equalsIgnoreCase("restore") && args[1].equalsIgnoreCase("stage")) {
            return filter(List.of("confirm", "--confirm"), args[2]);
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String option : options) {
            if (option.startsWith(lower)) {
                out.add(option);
            }
        }
        return out;
    }
}
