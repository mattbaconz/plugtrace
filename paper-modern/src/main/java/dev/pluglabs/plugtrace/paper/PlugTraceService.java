package dev.pluglabs.plugtrace.paper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.pluglabs.plugtrace.api.PlugTraceAPI;
import dev.pluglabs.plugtrace.api.PlugTraceBridge;
import dev.pluglabs.plugtrace.api.MigrationRecord;
import dev.pluglabs.plugtrace.api.VerificationCheck;
import dev.pluglabs.plugtrace.api.VerificationCheckDefinition;
import dev.pluglabs.plugtrace.api.VerificationContext;
import dev.pluglabs.plugtrace.api.VerificationExecution;
import dev.pluglabs.plugtrace.api.VerificationRegistration;
import dev.pluglabs.plugtrace.api.VerificationResult;
import dev.pluglabs.plugtrace.domain.Annotation;
import dev.pluglabs.plugtrace.domain.AttributionEngine;
import dev.pluglabs.plugtrace.domain.BaselineSelector;
import dev.pluglabs.plugtrace.domain.Change;
import dev.pluglabs.plugtrace.domain.ChangeType;
import dev.pluglabs.plugtrace.domain.ComponentSnapshot;
import dev.pluglabs.plugtrace.domain.Deployment;
import dev.pluglabs.plugtrace.domain.DeploymentHealth;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycle;
import dev.pluglabs.plugtrace.domain.DeploymentLifecycleReconstructor;
import dev.pluglabs.plugtrace.domain.DiffEngine;
import dev.pluglabs.plugtrace.domain.FingerprintEngine;
import dev.pluglabs.plugtrace.domain.Issue;
import dev.pluglabs.plugtrace.domain.IssueEvent;
import dev.pluglabs.plugtrace.domain.IssueStatus;
import dev.pluglabs.plugtrace.domain.NoiseRules;
import dev.pluglabs.plugtrace.domain.PlatformInfo;
import dev.pluglabs.plugtrace.domain.PlugDevIdentity;
import dev.pluglabs.plugtrace.domain.PlugDevIdentityLoader;
import dev.pluglabs.plugtrace.domain.RegressionClass;
import dev.pluglabs.plugtrace.domain.RegressionEngine;
import dev.pluglabs.plugtrace.domain.RestoreJournalCodec;
import dev.pluglabs.plugtrace.domain.RestorePlan;
import dev.pluglabs.plugtrace.domain.RestoreService;
import dev.pluglabs.plugtrace.domain.JarRetentionService;
import dev.pluglabs.plugtrace.domain.ServerNode;
import dev.pluglabs.plugtrace.domain.StateFingerprint;
import dev.pluglabs.plugtrace.domain.Suspect;
import dev.pluglabs.plugtrace.domain.TraceStore;
import dev.pluglabs.plugtrace.domain.Checkpoint;
import dev.pluglabs.plugtrace.domain.CheckpointPolicy;
import dev.pluglabs.plugtrace.domain.CheckCriticality;
import dev.pluglabs.plugtrace.domain.CheckResult;
import dev.pluglabs.plugtrace.domain.CheckStatus;
import dev.pluglabs.plugtrace.domain.ConfigResetDetector;
import dev.pluglabs.plugtrace.domain.DeploymentVerification;
import dev.pluglabs.plugtrace.domain.Incident;
import dev.pluglabs.plugtrace.domain.IncidentStatus;
import dev.pluglabs.plugtrace.domain.IncidentEngine;
import dev.pluglabs.plugtrace.domain.VerificationEngine;
import dev.pluglabs.plugtrace.domain.ExpectedState;
import dev.pluglabs.plugtrace.domain.RecoveryOutcome;
import dev.pluglabs.plugtrace.domain.RecoveryVerification;
import dev.pluglabs.plugtrace.domain.RecoveryVerificationEngine;
import dev.pluglabs.plugtrace.domain.StartupTimeRegressionDetector;
import dev.pluglabs.plugtrace.domain.MsptRegressionDetector;
import dev.pluglabs.plugtrace.report.ReportRequest;
import dev.pluglabs.plugtrace.report.ReportService;
import dev.pluglabs.plugtrace.report.RedactionService;
import dev.pluglabs.plugtrace.platform.SchedulerFacade;
import dev.pluglabs.plugtrace.platform.CapabilityRegistry;
import dev.pluglabs.plugtrace.storage.SqliteTraceStore;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public final class PlugTraceService implements AutoCloseable {
    private static final Set<String> ANNOTATION_CATEGORIES = Set.of(
            "ops", "migration", "traffic", "host", "season", "ddos", "other", "spark"
    );

    private final Logger logger;
    private final Path dataFolder;
    private final FileConfiguration config;
    private final TraceStore store;
    private final SnapshotCollector snapshotCollector;
    private final DiffEngine diffEngine = new DiffEngine();
    private final FingerprintEngine fingerprintEngine = new FingerprintEngine();
    private final BaselineSelector baselineSelector = new BaselineSelector();
    private final RegressionEngine regressionEngine = new RegressionEngine();
    private final AttributionEngine attributionEngine = new AttributionEngine();
    private final ReportService reportService = new ReportService();
    private final RedactionService redaction = new RedactionService();
    private final LinkedBlockingQueue<IssueEvent> queue = new LinkedBlockingQueue<>(10_000);
    private final AtomicLong droppedEvents = new AtomicLong();
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, Issue> issueBuffer = new ConcurrentHashMap<>();
    private final Map<String, String> safeFields = new ConcurrentHashMap<>();
    private final Map<String, List<String>> rawSamples = new ConcurrentHashMap<>();
    private final SchedulerFacade scheduler;
    private final VerificationEngine verificationEngine = new VerificationEngine();
    private final Map<String, RegisteredCheck> registeredChecks = new ConcurrentHashMap<>();
    private final IncidentEngine incidentEngine = new IncidentEngine();
    private final RecoveryVerificationEngine recoveryVerificationEngine = new RecoveryVerificationEngine();

    private String nodeId;
    private Deployment currentDeployment;
    private Deployment baseline;
    private List<Change> currentChanges = List.of();
    private List<Suspect> currentSuspects = List.of();
    private String baselineDescription = "No reliable baseline exists yet.";
    private PlatformInfo platformInfo = new PlatformInfo("paper", "Private alpha (unverified runtime)", "paper-modern");
    private NoiseRules noiseRules = NoiseRules.empty();
    private boolean sparkDetected;
    private String sparkVersion;
    private String sparkProfileUrl;
    private String lastHostedReportId;
    private String lastHostedShareUrl;
    private String lastHostedExpiresAt;
    private String lastHostedDeleteToken;
    private int retentionDeployments = 100;
    private int rawSamplesPerIssue = 3;
    private String privacyMode = "hash-only-config";
    private OperatorConfig operatorConfig;
    private final String artifactId;
    private PlugDevIdentity plugDevIdentity;
    private String lastReleaseContinuityKey;
    private JarRetentionService jarRetention;
    private RestoreService restoreService;
    private RestorePlan lastRestorePlan;
    private long lastSnapshotMillis;
    private final java.util.concurrent.ConcurrentLinkedQueue<Long> syncTickNanos =
            new java.util.concurrent.ConcurrentLinkedQueue<>();
    private volatile long lastSyncTickMicros;
    private volatile DeploymentVerification currentVerification;
    private volatile long startupReadyMillis = -1L;
    private final List<Double> msptSamples = new CopyOnWriteArrayList<>();
    private org.bukkit.Server server;
    private PluginManager pluginManager;
    private volatile StackOwnershipIndex ownershipIndex = StackOwnershipIndex.empty();

    public PlugTraceService(Logger logger, Path dataFolder, FileConfiguration config) {
        this(logger, dataFolder, config, "paper-modern", null);
    }

    public PlugTraceService(Logger logger, Path dataFolder, FileConfiguration config, String artifactId) {
        this(logger, dataFolder, config, artifactId, null);
    }

    public PlugTraceService(
            Logger logger,
            Path dataFolder,
            FileConfiguration config,
            String artifactId,
            SchedulerFacade scheduler
    ) {
        this.logger = logger;
        this.dataFolder = dataFolder;
        this.config = config;
        this.artifactId = artifactId == null || artifactId.isBlank() ? "paper-modern" : artifactId;
        this.scheduler = scheduler;
        this.store = new SqliteTraceStore(dataFolder.resolve("plugtrace.db"));
        this.snapshotCollector = new SnapshotCollector();
        applyOperatorConfig(OperatorConfig.from(config), true);
    }

    /**
     * Re-apply retention/privacy/verification snapshot values after {@code reloadConfig()}.
     * Expected lists are read live from {@link #config}; verification schedule is set at server-ready only.
     */
    public List<String> applyOperatorConfig(OperatorConfig next) {
        return applyOperatorConfig(next, false);
    }

    private List<String> applyOperatorConfig(OperatorConfig next, boolean initial) {
        this.operatorConfig = next;
        this.retentionDeployments = next.retentionDeployments;
        this.rawSamplesPerIssue = next.rawSamplesPerIssue;
        this.privacyMode = next.reportPrivacyMode();
        long maxRetainBytes = (long) next.jarMaxMb * 1024L * 1024L;
        this.jarRetention = new JarRetentionService(
                dataFolder, maxRetainBytes, next.jarVersionsPerComponent, next.jarRetentionEnabled, logger);
        this.restoreService = new RestoreService(dataFolder, jarRetention);
        if (!initial) {
            for (String warning : next.warnings) {
                logger.warning("PlugTrace config: " + warning);
            }
        } else {
            for (String warning : next.warnings) {
                logger.warning("PlugTrace config: " + warning);
            }
        }
        return next.warnings;
    }

    public OperatorConfig operatorConfig() {
        return operatorConfig;
    }

    public Map<String, Object> effectiveConfig() {
        Map<String, Object> map = new LinkedHashMap<>(operatorConfig == null
                ? OperatorConfig.from(config).toMap()
                : operatorConfig.toMap());
        map.put("expected.plugins", config.getStringList("expected.plugins"));
        map.put("expected.commands", config.getStringList("expected.commands"));
        map.put("expected.worlds", config.getStringList("expected.worlds"));
        map.put("expected.services", config.getStringList("expected.services"));
        return map;
    }

    private void runStoreAsync(Runnable task) {
        if (scheduler != null) {
            scheduler.runAsync(task);
        } else {
            // Tests / headless without Bukkit scheduler - run inline on a daemon thread.
            Thread t = new Thread(task, "plugtrace-worker-fallback");
            t.setDaemon(true);
            t.start();
        }
    }

    public void start(org.bukkit.Server server, PluginManager pluginManager) {
        this.server = server;
        this.pluginManager = pluginManager;
        try {
            Files.createDirectories(dataFolder);
            Files.createDirectories(dataFolder.resolve("reports"));
            Files.createDirectories(dataFolder.resolve("rules"));
        } catch (Exception e) {
            throw new IllegalStateException("Unable to create PlugTrace data folder", e);
        }

        this.noiseRules = loadNoiseRules();
        detectSpark(pluginManager, server);
        List<String> updaters = UpdaterCoexistence.detectNames(pluginManager);
        String updaterSummary = UpdaterCoexistence.summary(updaters);
        if (updaterSummary != null) {
            logger.info(updaterSummary);
        }

        this.nodeId = loadOrCreateNodeId(server);
        this.platformInfo = PlatformInfo.detect(
                server.getName(),
                server.getVersion() + " " + server.getBukkitVersion(),
                artifactId
        );
        if (platformInfo.migrateHint() != null) {
            logger.warning(platformInfo.migrateHint());
        }
        ServerNode node = new ServerNode(
                nodeId,
                server.getName() + "@" + server.getPort(),
                platformInfo.forkFamily(),
                "production",
                Instant.now()
        );
        store.upsertNode(node);

        long sequence = store.nextSequence(nodeId);
        List<Deployment> history = store.listDeployments(nodeId, Math.max(50, retentionDeployments));
        Path serverRoot = serverRoot(server);
        if (!history.isEmpty()) {
            Deployment previous = history.get(0);
            List<String> crashReports = CrashReportScanner.findSince(
                    serverRoot, previous.startedAt(), Instant.now());
            Deployment reconstructed = DeploymentLifecycleReconstructor.reconstruct(
                    previous, Instant.now(), crashReports);
            if (reconstructed != previous) {
                store.saveDeployment(reconstructed);
                List<Deployment> updated = new ArrayList<>(history);
                updated.set(0, reconstructed);
                history = List.copyOf(updated);
                logger.warning("Previous deployment #" + reconstructed.localSequence() + " ended "
                        + reconstructed.lifecycle().name().toLowerCase(Locale.ROOT)
                        + (crashReports.isEmpty() ? "" : "; crash reports=" + crashReports));
            }
        }
        String parentId = history.isEmpty() ? null : history.get(0).id();

        long snapshotStarted = System.nanoTime();
        Deployment draft = snapshotCollector.collect(server, pluginManager, nodeId, sequence, parentId);
        this.lastSnapshotMillis = (System.nanoTime() - snapshotStarted) / 1_000_000L;
        String fingerprint = StateFingerprint.compute(draft);
        this.currentDeployment = Deployment.builder()
                .id(draft.id())
                .localSequence(draft.localSequence())
                .nodeId(draft.nodeId())
                .parentId(draft.parentId())
                .stateFingerprint(fingerprint)
                .startedAt(draft.startedAt())
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.UNKNOWN)
                .healthReasons(List.of("Awaiting observation window / manual mark."))
                .complete(false)
                .tags(List.of())
                .serverImplementation(draft.serverImplementation())
                .minecraftVersion(draft.minecraftVersion())
                .javaVersion(draft.javaVersion())
                .javaVendor(draft.javaVendor())
                .components(draft.components())
                .configs(draft.configs())
                .schemaVersion(3)
                .build();

        Optional<Deployment> selectedBaseline = baselineSelector.select(history, currentDeployment);
        this.baseline = selectedBaseline.orElse(null);
        this.baselineDescription = baselineSelector.describe(selectedBaseline);
        this.currentChanges = diffEngine.diff(baseline, currentDeployment);
        store.saveDeployment(currentDeployment);
        List<ComponentSnapshot> ownershipComponents = currentDeployment.components();
        List<ComponentSnapshot> retainComponents = List.copyOf(ownershipComponents);
        runStoreAsync(() -> {
            ownershipIndex = StackOwnershipIndex.build(ownershipComponents, WrapperRegistry.packaged());
            for (ComponentSnapshot component : retainComponents) {
                jarRetention.retainIfPresent(component);
            }
        });

        runStoreAsync(() -> store.pruneDeployments(nodeId, retentionDeployments, currentDeployment.id()));
        recomputeSuspects();
        loadPlugDevIdentity(serverRoot);
        if (scheduler != null) {
            scheduler.startWorkerLoop(this::drainLoop);
        } else {
            runStoreAsync(this::drainLoop);
        }
        bindApi();

        PlugTraceMessages.consoleRitual(logger,
                "<gradient:#22d3ee:#2dd4bf><bold>initialized</bold></gradient> <dark_gray>-</dark_gray> <aqua>"
                        + PlugTraceMessages.escape(artifactId) + "</aqua>");
        PlugTraceMessages.consoleRitual(logger,
                "<gray>Deployment</gray> <aqua>#" + currentDeployment.localSequence() + "</aqua> <gray>recorded.</gray>");
        if (updaterSummary != null) {
            annotate("ops", updaterSummary, "plugtrace", null);
        }
        PlugTraceMessages.consoleRitual(logger, baseline == null
                ? "<gold>!</gold> <gray>No previous healthy baseline exists yet.</gray>"
                : "<gray>Baseline:</gray> <white>" + PlugTraceMessages.escape(baselineDescription) + "</white>");
        if (baseline == null) {
            PlugTraceMessages.consoleRitual(logger,
                    "<gray>Install-before-break: after first</gray> <green>+ HEALTHY</green> "
                            + "<gray>→</gray> <aqua>/plugtrace checkpoint</aqua> "
                            + "<gray>+</gray> <aqua>expected capture</aqua>");
            PlugTraceMessages.consoleRitual(logger,
                    "<gray>Ritual: after every risky restart, read</gray> <aqua>/plugtrace status</aqua>");
        }
        PlugTraceMessages.consoleRitual(logger,
                "<gray>Core history is local; nothing has been uploaded.</gray>");
        PlugTraceMessages.consoleRitual(logger,
                "<gray>Platform</gray> <white>" + PlugTraceMessages.escape(platformInfo.forkFamily())
                        + " " + PlugTraceMessages.escape(currentDeployment.minecraftVersion())
                        + "</white> <dark_gray>|</dark_gray> <gray>"
                        + PlugTraceMessages.escape(platformInfo.supportTier())
                        + "</gray> <dark_gray>|</dark_gray> <aqua>"
                        + PlugTraceMessages.escape(platformInfo.artifact())
                        + "</aqua> <dark_gray>|</dark_gray> <gray>spark </gray>"
                        + (sparkDetected
                        ? "<green>" + PlugTraceMessages.escape(sparkVersion) + "</green>"
                        : "<dark_gray>absent</dark_gray>")
                        + " <dark_gray>|</dark_gray> <gray>"
                        + (scheduler == null ? "fallback" : (scheduler.isFolia() ? "folia-facade" : "bukkit-facade"))
                        + "</gray>");
        if (sparkDetected) {
            PlugTraceMessages.consoleRitual(logger,
                    "<green>+</green> <gray>spark detected - lag → spark; regressions → PlugTrace.</gray> "
                            + "<aqua>/plugtrace spark link &lt;url&gt;</aqua>");
        }
        if (plugDevIdentity != null) {
            PlugTraceMessages.consoleRitual(logger,
                    "<aqua>*</aqua> <gray>PlugDev</gray> <white>"
                            + PlugTraceMessages.escape(plugDevIdentity.projectName())
                            + "</white> <dark_gray>@</dark_gray><aqua>"
                            + PlugTraceMessages.escape(plugDevIdentity.shortCommit())
                            + "</aqua>"
                            + (plugDevIdentity.gitDirty() ? " <gold>(dirty)</gold>" : ""));
        }
        checkPendingRestoreComplete();
    }

    private void loadPlugDevIdentity(Path serverRoot) {
        Path lastKeyFile = dataFolder.resolve("last-plugdev-continuity.txt");
        try {
            if (Files.isRegularFile(lastKeyFile)) {
                lastReleaseContinuityKey = Files.readString(lastKeyFile).trim();
            }
        } catch (Exception ignored) {
            // best-effort
        }
        Optional<PlugDevIdentity> loaded = PlugDevIdentityLoader.load(dataFolder, serverRoot);
        loaded.ifPresent(this::applyReleaseIdentity);
    }

    public void recordReleaseIdentity(Map<String, ?> identityMap) {
        PlugDevIdentity identity = PlugDevIdentity.fromMap(identityMap);
        if (identity != null) {
            applyReleaseIdentity(identity);
        }
    }

    private void applyReleaseIdentity(PlugDevIdentity identity) {
        this.plugDevIdentity = identity;
        String key = identity.continuityKey();
        if (lastReleaseContinuityKey != null
                && !lastReleaseContinuityKey.isBlank()
                && !lastReleaseContinuityKey.equals(key)) {
            annotate("ops", "PlugDev rebuild: " + identity.shortCommit()
                    + " — bootstrap/JVM/config churn may DEGRADE without meaning a production update failed",
                    "plugdev", null);
        } else if (lastReleaseContinuityKey == null || lastReleaseContinuityKey.isBlank()) {
            annotate("ops", "PlugDev identity present (" + identity.projectName()
                    + "). Author-loop churn (PlugDev-Bootstrap, JVM, configs) is known context — "
                    + "see rules/noise-v1.json and /plugtrace status noise row.",
                    "plugdev", null);
        }
        lastReleaseContinuityKey = key;
        try {
            Files.writeString(dataFolder.resolve("last-plugdev-continuity.txt"), key);
        } catch (Exception e) {
            logger.warning("Unable to persist PlugDev continuity key: " + e.getMessage());
        }
    }

    private void bindApi() {
        PlugTraceAPI.bind(new PlugTraceBridge() {
            @Override
            public boolean isAvailable() {
                return running.get();
            }

            @Override
            public void annotate(String category, String text) {
                PlugTraceService.this.annotate(category, text, "plugin-api", null);
            }

            @Override
            public void recordSafeField(String pluginName, String key, Object value) {
                if (pluginName == null || key == null || value == null) {
                    return;
                }
                if (!(value instanceof String || value instanceof Number || value instanceof Boolean)) {
                    return;
                }
                String stored = String.valueOf(value);
                String probe = key + "=" + stored;
                String redacted = redaction.redact(probe);
                safeFields.put(pluginName.toLowerCase(Locale.ROOT) + ":" + key.toLowerCase(Locale.ROOT), redacted);
                PlugTraceService.this.annotate("ops", "safe-field " + pluginName + "." + key + "=" + redacted, "plugin-api", null);
            }

            @Override
            public void recordReleaseIdentity(Map<String, ?> identity) {
                PlugTraceService.this.recordReleaseIdentity(identity);
            }

            @Override
            public VerificationRegistration registerVerificationCheck(
                    VerificationCheckDefinition definition, VerificationCheck check) {
                Objects.requireNonNull(definition, "definition");
                Objects.requireNonNull(check, "check");
                String id = definition.qualifiedId();
                registeredChecks.put(id, new RegisteredCheck(definition, check));
                return () -> registeredChecks.remove(id);
            }

            @Override
            public void recordMigration(MigrationRecord migration) {
                if (migration != null) {
                    PlugTraceService.this.annotate("migration", migration.pluginId() + " schema " + migration.fromSchema()
                            + " -> " + migration.toSchema() + " rollback=" + migration.rollbackSafety()
                            + " " + migration.summary(), "plugin-api", null);
                }
            }
        });
    }

    /** Called from ServerLoadEvent; verification never guesses that onEnable means the server is ready. */
    public void onServerReady() {
        // Paper's bundled spark initializes after ordinary plugins, so refresh detection at ready time.
        detectSpark(pluginManager, server);
        startupReadyMillis = Math.max(0L, System.currentTimeMillis()
                - java.lang.management.ManagementFactory.getRuntimeMXBean().getStartTime());
        currentDeployment = currentDeployment.withReadyEvidence(Instant.now(), startupReadyMillis);
        Deployment readyDeployment = currentDeployment;
        runStoreAsync(() -> store.saveDeployment(readyDeployment));
        annotate("ops", "startup-ready-ms=" + startupReadyMillis, "plugtrace", null);
        if (scheduler == null) {
            runVerificationAsync(false);
            return;
        }
        long initialTicks = Math.max(1L, (operatorConfig == null
                ? config.getLong("verification.initialDelaySeconds", 30L)
                : operatorConfig.initialDelaySeconds) * 20L);
        long observationTicks = Math.max(initialTicks,
                (operatorConfig == null
                        ? config.getLong("verification.observationMinutes", 15L)
                        : operatorConfig.observationMinutes) * 60L * 20L);
        scheduler.runDelayedSync(() -> runVerificationAsync(false), initialTicks);
        scheduler.runRepeatingSync(this::sampleMspt, 20L, 30L * 20L);
        scheduler.runDelayedSync(() -> runVerificationAsync(false), 5L * 60L * 20L);
        scheduler.runDelayedSync(() -> runVerificationAsync(true), observationTicks);
    }

    public CompletionStage<DeploymentVerification> runVerificationAsync(boolean observationComplete) {
        List<CheckResult> checks = new ArrayList<>();
        checks.add(CheckResult.pass("server-ready", "Server ready"));
        checks.addAll(checkExpectedPlugins());
        checks.addAll(checkExpectedCommands());
        checks.addAll(checkExpectedWorlds());
        checks.addAll(checkExpectedServices());
        checks.addAll(ProviderServiceChecks.run(server, server.getPluginManager(), logger));
        checks.addAll(checkDependencies());
        checks.add(checkStartupTimeRegression());
        checks.add(checkMsptRegression());
        checks.addAll(checkConfigResets());

        boolean severe = currentIssues().stream().anyMatch(issue -> {
            String severity = issue.severity() == null ? "" : issue.severity().toLowerCase(Locale.ROOT);
            return issue.status() == IssueStatus.NEW && (severity.contains("error") || severity.contains("severe"));
        });
        checks.add(new CheckResult("new-severe-issues", "New severe issues",
                severe ? CheckStatus.WARN : CheckStatus.PASS, CheckCriticality.WARNING,
                severe ? "New severe issue fingerprints appeared after this deployment" : "No new severe issues",
                Map.of("count", currentIssues().stream().filter(i -> i.status() == IssueStatus.NEW).count())));

        Executor asyncExecutor = scheduler == null ? CompletableFuture.delayedExecutor(0, TimeUnit.MILLISECONDS)
                : scheduler.worker();
        List<CompletableFuture<CheckResult>> custom = registeredChecks.values().stream()
                .map(registration -> startRegisteredCheck(registration, asyncExecutor))
                .toList();
        return CompletableFuture.allOf(custom.toArray(CompletableFuture[]::new))
                .thenApply(ignored -> {
                    custom.forEach(future -> checks.add(future.join()));
                    return finishVerification(checks, observationComplete, severe);
                });
    }

    /** Safe entry point for HTTP/console callers whose current thread is unknown. */
    public void requestVerification(boolean observationComplete) {
        if (scheduler == null) {
            runVerificationAsync(observationComplete);
        } else {
            scheduler.runDelayedSync(() -> runVerificationAsync(observationComplete), 1L);
        }
    }

    private synchronized DeploymentVerification finishVerification(
            List<CheckResult> checks, boolean observationComplete, boolean severe) {
        currentVerification = verificationEngine.evaluate(UUID.randomUUID().toString(), currentDeployment.id(),
                Instant.now(), checks, observationComplete, severe);
        store.saveVerification(currentVerification);
        List<String> reasons = checks.stream()
                .filter(result -> result.status() == CheckStatus.FAIL || result.status() == CheckStatus.WARN)
                .map(CheckResult::summary).limit(8).toList();
        currentDeployment = currentDeployment.withHealth(currentVerification.health(), reasons);
        store.saveDeployment(currentDeployment);
        if (currentVerification.health() == DeploymentHealth.FAILING
                || currentVerification.health() == DeploymentHealth.DEGRADED) {
            List<String> failed = checks.stream().filter(r -> r.status() == CheckStatus.FAIL || r.status() == CheckStatus.WARN)
                    .map(CheckResult::checkId).toList();
            List<String> issueFingerprints = currentIssues().stream().filter(i -> i.status() == IssueStatus.NEW)
                    .map(Issue::fingerprint).toList();
            boolean alreadyOpen = store.listIncidents(currentDeployment.id(), 100).stream()
                    .anyMatch(incident -> incident.status() == IncidentStatus.OPEN);
            if (!alreadyOpen) {
                Incident incident = new Incident(UUID.randomUUID().toString(), currentDeployment.id(),
                        currentVerification.id(), Instant.now(), null, IncidentStatus.OPEN,
                        "Deployment verification " + currentVerification.health().name().toLowerCase(Locale.ROOT),
                        issueFingerprints, failed);
                store.saveIncident(incident);
            }
        }
        announceVerificationOutcome(checks, observationComplete);
        return currentVerification;
    }

    /** Console ritual surface: PASS/FAIL + next actions (D-034 packaging). */
    private void announceVerificationOutcome(List<CheckResult> checks, boolean observationComplete) {
        DeploymentHealth health = currentVerification.health();
        String window = observationComplete ? "observation complete" : "early check";
        PlugTraceMessages.consoleRitual(logger,
                PlugTraceMessages.healthMini(health)
                        + " <dark_gray>-</dark_gray> <gray>deployment</gray> <aqua>#"
                        + currentDeployment.localSequence() + "</aqua> <dark_gray>("
                        + PlugTraceMessages.escape(window) + ")</dark_gray> "
                        + "<dark_gray>- ritual:</dark_gray> <white>/plugtrace status</white>");

        if (health == DeploymentHealth.HEALTHY && observationComplete) {
            boolean noCheckpoint = checkpoints(1).isEmpty();
            if (noCheckpoint) {
                PlugTraceMessages.consoleRitual(logger,
                        "<green>+</green> <white>First HEALTHY window</white> <gray>- lock a baseline now:</gray>");
                PlugTraceMessages.consoleRitual(logger, "<dark_gray>  </dark_gray><aqua>/plugtrace checkpoint first-healthy</aqua>");
                PlugTraceMessages.consoleRitual(logger, "<dark_gray>  </dark_gray><aqua>/plugtrace expected capture</aqua>");
                PlugTraceMessages.consoleRitual(logger, "<dark_gray>  </dark_gray><aqua>/plugtrace mark healthy</aqua>");
                PlugTraceMessages.consoleRitual(logger,
                        "<gray>Install-before-break: without a checkpoint, PlugTrace cannot invent last night's healthy state.</gray>");
            } else if (baseline == null) {
                PlugTraceMessages.consoleRitual(logger,
                        "<green>+</green> <white>HEALTHY</white> <gray>- consider</gray> <aqua>/plugtrace mark healthy</aqua> "
                                + "<gray>so the next restart diffs against this deployment.</gray>");
            }
            return;
        }

        if (health != DeploymentHealth.FAILING && health != DeploymentHealth.DEGRADED) {
            return;
        }

        PlugTraceMessages.bannerOpen(PlugTraceMessages.console(), health);
        checks.stream()
                .filter(r -> r.status() == CheckStatus.FAIL || r.status() == CheckStatus.WARN)
                .limit(8)
                .forEach(r -> PlugTraceMessages.consoleRitualWarn(logger,
                        (r.status() == CheckStatus.FAIL
                                ? "<red>x</red> "
                                : "<gold>!</gold> ")
                                + "<white>" + PlugTraceMessages.escape(r.checkId()) + "</white> "
                                + "<dark_gray>-</dark_gray> <gray>"
                                + PlugTraceMessages.escape(r.summary()) + "</gray>"));

        List<Change> jarChanges = currentChanges == null ? List.of() : currentChanges.stream()
                .filter(c -> c.type() == ChangeType.VERSION_CHANGED
                        || c.type() == ChangeType.BINARY_CHANGED_SAME_VERSION
                        || c.type() == ChangeType.COMPONENT_ADDED
                        || c.type() == ChangeType.COMPONENT_REMOVED)
                .sorted((a, b) -> Integer.compare(b.significance(), a.significance()))
                .limit(5)
                .toList();
        if (!jarChanges.isEmpty()) {
            PlugTraceMessages.consoleRitualWarn(logger,
                    "<gradient:#22d3ee:#2dd4bf>Top changed JARs / versions</gradient>");
            jarChanges.forEach(c -> PlugTraceMessages.consoleRitualWarn(logger,
                    "<dark_gray>-</dark_gray> <aqua>" + PlugTraceMessages.escape(String.valueOf(c.type()))
                            + "</aqua> <white>" + PlugTraceMessages.escape(c.componentKey()) + "</white>"
                            + (c.explanation().isBlank() ? ""
                            : " <dark_gray>-</dark_gray> <gray>"
                            + PlugTraceMessages.escape(c.explanation()) + "</gray>")));
        } else {
            PlugTraceMessages.consoleRitualWarn(logger,
                    "<gray>No JAR/version deltas vs baseline (or no baseline yet).</gray>");
        }

        PlugTraceMessages.consoleRitualWarn(logger,
                "<white>Next:</white> <aqua>/plugtrace restore preview</aqua>");
        PlugTraceMessages.consoleRitualWarn(logger,
                "<white>Share:</white> <aqua>/plugtrace report upload</aqua> "
                        + "<dark_gray>(or</dark_gray> <aqua>report preview</aqua><dark_gray>)</dark_gray>");

        boolean msptWarn = checks.stream().anyMatch(r ->
                "mspt-regression".equals(r.checkId())
                        && (r.status() == CheckStatus.WARN || r.status() == CheckStatus.FAIL));
        if (msptWarn) {
            if (sparkDetected) {
                PlugTraceMessages.consoleRitualWarn(logger,
                        "<gold>!</gold> <gray>MSPT + spark - capture a profile, then</gray> "
                                + "<aqua>/plugtrace spark link &lt;url&gt;</aqua>");
            } else {
                PlugTraceMessages.consoleRitualWarn(logger,
                        "<gold>!</gold> <gray>MSPT regression - use spark for lag; PlugTrace owns what changed.</gray>");
            }
        }
        PlugTraceMessages.bannerClose(PlugTraceMessages.console(), health);
    }

    /** Headless/tests only. Bukkit command and lifecycle paths use the non-blocking method. */
    public DeploymentVerification runVerification(boolean observationComplete) {
        return runVerificationAsync(observationComplete).toCompletableFuture().join();
    }

    private List<CheckResult> checkExpectedPlugins() {
        Set<String> enabled = java.util.Arrays.stream(pluginManager.getPlugins()).filter(Plugin::isEnabled)
                .map(p -> p.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        Set<String> expected = new java.util.LinkedHashSet<>();
        expectedState().ifPresent(state -> state.plugins().stream()
                .map(s -> s.toLowerCase(Locale.ROOT)).forEach(expected::add));
        if (baseline != null) baseline.components().stream().filter(ComponentSnapshot::enabled)
                .filter(c -> c.identity().type() == dev.pluglabs.plugtrace.domain.ComponentType.PLUGIN)
                .map(c -> c.identity().normalizedName().toLowerCase(Locale.ROOT)).forEach(expected::add);
        config.getStringList("expected.plugins").stream().map(s -> s.toLowerCase(Locale.ROOT)).forEach(expected::add);
        List<String> missing = expected.stream().filter(name -> !enabled.contains(name)).sorted().toList();
        return List.of(new CheckResult("expected-plugins", "Expected plugins",
                missing.isEmpty() ? CheckStatus.PASS : CheckStatus.FAIL, CheckCriticality.CRITICAL,
                missing.isEmpty() ? "All expected plugins are enabled" : "Missing or disabled: " + String.join(", ", missing),
                Map.of("expected", expected.size(), "missing", missing)));
    }

    private List<CheckResult> checkExpectedCommands() {
        List<String> configured = new ArrayList<>(config.getStringList("expected.commands"));
        expectedState().ifPresent(state -> configured.addAll(state.commands()));
        List<String> missing = configured.stream().distinct()
                .filter(name -> server.getPluginCommand(name) == null).sorted().toList();
        return List.of(new CheckResult("expected-commands", "Expected commands",
                missing.isEmpty() ? CheckStatus.PASS : CheckStatus.FAIL, CheckCriticality.CRITICAL,
                missing.isEmpty() ? "Expected commands are registered" : "Missing commands: " + String.join(", ", missing),
                Map.of("missing", missing)));
    }

    private List<CheckResult> checkExpectedWorlds() {
        Set<String> worlds = server.getWorlds().stream().map(w -> w.getName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> configured = new ArrayList<>(config.getStringList("expected.worlds"));
        expectedState().ifPresent(state -> configured.addAll(state.worlds()));
        List<String> missing = configured.stream().distinct()
                .filter(name -> !worlds.contains(name.toLowerCase(Locale.ROOT))).sorted().toList();
        return List.of(new CheckResult("expected-worlds", "Expected worlds",
                missing.isEmpty() ? CheckStatus.PASS : CheckStatus.FAIL, CheckCriticality.CRITICAL,
                missing.isEmpty() ? "Expected worlds are loaded" : "Missing worlds: " + String.join(", ", missing),
                Map.of("missing", missing)));
    }

    private List<CheckResult> checkExpectedServices() {
        List<String> missing = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        List<String> configured = new ArrayList<>(config.getStringList("expected.services"));
        expectedState().ifPresent(state -> configured.addAll(state.services()));
        for (String className : configured.stream().distinct().toList()) {
            try {
                Class<?> serviceClass = Class.forName(className, false, getClass().getClassLoader());
                if (server.getServicesManager().getRegistration(serviceClass) == null) missing.add(className);
            } catch (ClassNotFoundException e) {
                unknown.add(className);
            }
        }
        CheckStatus status = !missing.isEmpty() ? CheckStatus.FAIL : (!unknown.isEmpty() ? CheckStatus.UNKNOWN : CheckStatus.PASS);
        return List.of(new CheckResult("expected-services", "Expected services", status, CheckCriticality.CRITICAL,
                missing.isEmpty() && unknown.isEmpty() ? "Expected services are registered"
                        : "Missing=" + missing + " unknown=" + unknown, Map.of("missing", missing, "unknown", unknown)));
    }

    private List<CheckResult> checkDependencies() {
        Set<String> present = currentDeployment.components().stream()
                .map(c -> c.identity().normalizedName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        List<String> missing = new ArrayList<>();
        currentDeployment.components().stream().filter(ComponentSnapshot::loaded).forEach(component ->
                component.identity().dependencies().stream().filter(dep -> !present.contains(dep.toLowerCase(Locale.ROOT)))
                        .forEach(dep -> missing.add(component.identity().normalizedName() + " -> " + dep)));
        return List.of(new CheckResult("hard-dependencies", "Hard dependencies",
                missing.isEmpty() ? CheckStatus.PASS : CheckStatus.FAIL, CheckCriticality.CRITICAL,
                missing.isEmpty() ? "Hard dependencies are present" : "Missing dependencies: " + String.join(", ", missing),
                Map.of("missing", missing)));
    }

    private List<CheckResult> checkConfigResets() {
        if (baseline == null) return List.of(new CheckResult("config-reset", "Config reset detection",
                CheckStatus.SKIPPED, CheckCriticality.WARNING, "No healthy baseline", Map.of()));
        Map<String, dev.pluglabs.plugtrace.domain.ConfigSnapshot> before = baseline.configs().stream()
                .collect(Collectors.toMap(dev.pluglabs.plugtrace.domain.ConfigSnapshot::key, c -> c, (a, b) -> a));
        Set<String> currentKeys = currentDeployment.configs().stream()
                .map(dev.pluglabs.plugtrace.domain.ConfigSnapshot::key).collect(Collectors.toSet());
        List<String> resets = currentDeployment.configs().stream()
                .filter(after -> ConfigResetDetector.possibleReset(before.get(after.key()), after))
                .map(dev.pluglabs.plugtrace.domain.ConfigSnapshot::key).sorted().toList();
        List<String> deleted = before.keySet().stream().filter(key -> !currentKeys.contains(key)).sorted().toList();
        boolean warning = !resets.isEmpty() || !deleted.isEmpty();
        return List.of(new CheckResult("config-reset", "Config reset detection",
                warning ? CheckStatus.WARN : CheckStatus.PASS, CheckCriticality.WARNING,
                !warning ? "No deletion or structural reset detected"
                        : "Possible resets=" + resets + " deleted=" + deleted,
                Map.of("possibleResets", resets, "deleted", deleted)));
    }

    private CheckResult checkStartupTimeRegression() {
        if (startupReadyMillis < 0 || baseline == null) {
            return new CheckResult("startup-time", "Startup ready time", CheckStatus.SKIPPED,
                    CheckCriticality.WARNING, "No comparable healthy startup timing", Map.of());
        }
        long before = baseline.startupReadyMillis();
        if (before <= 0) {
            return new CheckResult("startup-time", "Startup ready time", CheckStatus.SKIPPED,
                    CheckCriticality.WARNING, "Healthy baseline has no startup timing", Map.of("currentMs", startupReadyMillis));
        }
        boolean regressed = StartupTimeRegressionDetector.material(before, startupReadyMillis);
        return new CheckResult("startup-time", "Startup ready time",
                regressed ? CheckStatus.WARN : CheckStatus.PASS, CheckCriticality.WARNING,
                regressed ? "Startup ready time increased by at least 10s and 50%" : "No material startup-time regression",
                Map.of("baselineMs", before, "currentMs", startupReadyMillis));
    }

    private void sampleMspt() {
        // Keep the sync/region tick path tiny: schedule MSPT probe off-thread.
        long started = System.nanoTime();
        try {
            runStoreAsync(this::sampleMsptAsync);
        } finally {
            long elapsed = System.nanoTime() - started;
            lastSyncTickMicros = elapsed / 1_000L;
            syncTickNanos.add(elapsed);
            while (syncTickNanos.size() > 64) {
                syncTickNanos.poll();
            }
        }
    }

    private void sampleMsptAsync() {
        ServerMsptProbe.sample(server).ifPresent(sample -> {
            msptSamples.add(sample);
            while (msptSamples.size() > 10) {
                msptSamples.remove(0);
            }
        });
    }

    private double syncTickP95Micros() {
        List<Long> samples = new ArrayList<>(syncTickNanos);
        if (samples.isEmpty()) {
            return 0.0;
        }
        samples.sort(Long::compareTo);
        int index = Math.min(samples.size() - 1, (int) Math.ceil(samples.size() * 0.95) - 1);
        if (index < 0) {
            index = 0;
        }
        return samples.get(index) / 1_000.0;
    }

    private CheckResult checkMsptRegression() {
        Double baselineMedian = null;
        if (baseline != null) {
            baselineMedian = store.findLatestVerification(baseline.id())
                    .flatMap(verification -> verification.checks().stream()
                            .filter(check -> "mspt-regression".equals(check.checkId()))
                            .map(CheckResult::safeDetails)
                            .map(details -> details.get("currentMedianMspt"))
                            .filter(Number.class::isInstance)
                            .map(Number.class::cast)
                            .map(Number::doubleValue)
                            .findFirst())
                    .orElse(null);
        }
        return MsptRegressionDetector.check(baselineMedian, List.copyOf(msptSamples));
    }

    private Path serverRoot(org.bukkit.Server bukkitServer) {
        if (bukkitServer.getWorldContainer() != null) {
            return bukkitServer.getWorldContainer().toPath().toAbsolutePath().normalize();
        }
        Path plugins = dataFolder.getParent();
        return plugins != null && plugins.getParent() != null
                ? plugins.getParent().toAbsolutePath().normalize()
                : dataFolder.toAbsolutePath().normalize();
    }

    private CompletableFuture<CheckResult> startRegisteredCheck(RegisteredCheck registration, Executor asyncExecutor) {
        VerificationCheckDefinition definition = registration.definition();
        try {
            CompletionStage<VerificationResult> stage;
            VerificationContext context = new VerificationContext(currentDeployment.id(), scheduler != null && scheduler.isFolia());
            if (definition.execution() == VerificationExecution.ASYNC) {
                stage = CompletableFuture.supplyAsync(() -> registration.check().run(context), asyncExecutor)
                        .thenCompose(value -> value);
            } else {
                // Caller is the Bukkit/Paper global scheduler (Folia global region scheduler).
                stage = registration.check().run(context);
            }
            return stage.toCompletableFuture()
                    .orTimeout(definition.timeout().toMillis(), TimeUnit.MILLISECONDS)
                    .handle((result, error) -> error == null
                            ? new CheckResult(definition.qualifiedId(), definition.displayName(),
                            CheckStatus.valueOf(result.status().name()), CheckCriticality.valueOf(definition.criticality().name()),
                            result.summary(), result.safeDetails())
                            : unknownRegisteredCheck(definition, error));
        } catch (Exception e) {
            return CompletableFuture.completedFuture(unknownRegisteredCheck(definition, e));
        }
    }

    private static CheckResult unknownRegisteredCheck(VerificationCheckDefinition definition, Throwable error) {
        return new CheckResult(definition.qualifiedId(), definition.displayName(), CheckStatus.UNKNOWN,
                CheckCriticality.valueOf(definition.criticality().name()),
                "Check did not complete: " + error.getClass().getSimpleName(), Map.of());
    }

    public Checkpoint createCheckpoint(String name, String actor) {
        CheckpointPolicy.requireHealthy(currentDeployment);
        Checkpoint checkpoint = new Checkpoint(UUID.randomUUID().toString(), currentDeployment.id(), name,
                Instant.now(), actor);
        store.saveCheckpoint(checkpoint);
        return checkpoint;
    }

    public DeploymentVerification currentVerification() { return currentVerification; }

    public List<Incident> currentIncidents() { return store.listIncidents(currentDeployment.id(), 100); }

    public List<Checkpoint> checkpoints(int limit) { return store.listCheckpoints(nodeId, limit); }

    public ExpectedState captureExpectedState(List<String> plugins, List<String> commands,
                                              List<String> worlds, List<String> services) {
        ExpectedState state = new ExpectedState(UUID.randomUUID().toString(), nodeId, currentDeployment.id(),
                Instant.now(), plugins, commands, worlds, services);
        store.saveExpectedState(state);
        return state;
    }

    public Optional<ExpectedState> expectedState() { return store.findExpectedState(nodeId); }

    private record RegisteredCheck(VerificationCheckDefinition definition, VerificationCheck check) { }

    private void detectSpark(PluginManager pluginManager, org.bukkit.Server server) {
        SparkDetector.Detection detection = SparkDetector.detect(pluginManager, server);
        sparkDetected = detection.detected();
        sparkVersion = detection.version();
    }

    private NoiseRules loadNoiseRules() {
        Path rulesFile = dataFolder.resolve("rules").resolve("noise-v1.json");
        try {
            if (!Files.exists(rulesFile)) {
                Files.createDirectories(rulesFile.getParent());
                Files.writeString(rulesFile, """
                        {
                          "suppressedFingerprints": [],
                          "suppressedMessageSubstrings": [],
                          "knownChurnComponents": [
                            "PlugDev-Bootstrap",
                            "plugdev-bootstrap",
                            "PLUGIN:plugdev-bootstrap"
                          ],
                          "knownChurnMessageSubstrings": [
                            "plugdev-bootstrap",
                            "PlugDev-Bootstrap"
                          ]
                        }
                        """);
            }
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(Files.readString(rulesFile));
            List<String> fingerprints = new ArrayList<>();
            List<String> substrings = new ArrayList<>();
            List<String> churnComponents = new ArrayList<>();
            List<String> churnMessages = new ArrayList<>();
            if (root.has("suppressedFingerprints")) {
                root.get("suppressedFingerprints").forEach(n -> fingerprints.add(n.asText()));
            }
            if (root.has("suppressedMessageSubstrings")) {
                root.get("suppressedMessageSubstrings").forEach(n -> substrings.add(n.asText()));
            }
            if (root.has("knownChurnComponents")) {
                root.get("knownChurnComponents").forEach(n -> churnComponents.add(n.asText()));
            }
            if (root.has("knownChurnMessageSubstrings")) {
                root.get("knownChurnMessageSubstrings").forEach(n -> churnMessages.add(n.asText()));
            }
            if (churnComponents.isEmpty() && churnMessages.isEmpty()
                    && fingerprints.isEmpty() && substrings.isEmpty()) {
                // Fresh empty file from older templates — still apply PlugDev context defaults.
                NoiseRules defaults = NoiseRules.plugDevDefaults();
                churnComponents.addAll(defaults.knownChurnComponents());
                churnMessages.addAll(List.of("plugdev-bootstrap", "PlugDev-Bootstrap"));
            }
            return NoiseRules.fromLists(fingerprints, substrings, churnComponents, churnMessages);
        } catch (Exception e) {
            logger.warning("Failed to load noise rules: " + e.getMessage());
            return NoiseRules.plugDevDefaults();
        }
    }

    /**
     * Ritual-first status payload for console + local web: health, top changes,
     * strongest suspect, known-churn context, and the next operator command.
     */
    public Map<String, Object> ritualStatus() {
        Map<String, Object> out = new LinkedHashMap<>();
        DeploymentHealth health = currentDeployment.health();
        out.put("health", health.name());
        out.put("deploymentSequence", currentDeployment.localSequence());
        out.put("deploymentId", currentDeployment.id());
        out.put("baseline", baselineDescription);
        List<Change> topChanges = currentChanges == null ? List.of() : currentChanges.stream()
                .sorted((a, b) -> Integer.compare(b.significance(), a.significance()))
                .limit(3)
                .toList();
        out.put("topChanges", topChanges.stream().map(c -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("type", c.type().name());
            row.put("component", c.componentKey());
            row.put("explanation", c.explanation());
            row.put("significance", c.significance());
            row.put("knownChurn", noiseRules.isKnownChurnChange(c));
            return row;
        }).toList());
        out.put("changeCount", currentChanges == null ? 0 : currentChanges.size());
        out.put("issueCount", issueBuffer.size());
        if (currentSuspects.isEmpty()) {
            out.put("strongestSuspect", null);
        } else {
            Suspect top = currentSuspects.get(0);
            Map<String, Object> suspect = new LinkedHashMap<>();
            suspect.put("component", top.componentKey());
            suspect.put("band", top.band().name());
            suspect.put("summary", top.changeSummary());
            suspect.put("knownChurn", noiseRules.isKnownChurnComponent(top.componentKey())
                    || noiseRules.isKnownChurnMessage(top.changeSummary()));
            out.put("strongestSuspect", suspect);
        }
        long churnChanges = currentChanges == null ? 0 : currentChanges.stream()
                .filter(noiseRules::isKnownChurnChange).count();
        long churnIssues = issueBuffer.values().stream()
                .filter(noiseRules::isKnownChurnIssue).count();
        Map<String, Object> noise = new LinkedHashMap<>();
        noise.put("knownChurnChangeCount", churnChanges);
        noise.put("knownChurnIssueCount", churnIssues);
        noise.put("plugDevPresent", plugDevIdentity != null);
        noise.put("hint", churnChanges > 0 || churnIssues > 0 || plugDevIdentity != null
                ? "PlugDev/runtime churn can DEGRADE without meaning a production update failed. Annotate context; suppress only fingerprints you trust."
                : null);
        out.put("noiseContext", noise);
        out.put("nextCommand", suggestNextCommand(health));
        out.put("nextCommands", suggestNextCommands(health));
        if (currentVerification != null) {
            out.put("verificationHealth", currentVerification.health().name());
            out.put("verificationChecks", currentVerification.checks().size());
        } else {
            out.put("verificationHealth", null);
            out.put("verificationChecks", 0);
        }
        return out;
    }

    public String suggestNextCommand(DeploymentHealth health) {
        List<String> cmds = suggestNextCommands(health);
        return cmds.isEmpty() ? "/plugtrace status" : cmds.get(0);
    }

    public List<String> suggestNextCommands(DeploymentHealth health) {
        DeploymentHealth h = health == null ? DeploymentHealth.UNKNOWN : health;
        return switch (h) {
            case HEALTHY -> {
                if (checkpoints(1).isEmpty()) {
                    yield List.of(
                            "/plugtrace checkpoint first-healthy",
                            "/plugtrace expected capture",
                            "/plugtrace mark healthy"
                    );
                }
                if (baseline == null) {
                    yield List.of("/plugtrace mark healthy", "/plugtrace report preview");
                }
                yield List.of("/plugtrace report preview", "/plugtrace checkpoint");
            }
            case FAILING, CRASHED -> List.of(
                    "/plugtrace restore preview",
                    "/plugtrace report upload",
                    "/plugtrace suspect"
            );
            case DEGRADED -> List.of(
                    "/plugtrace suspect",
                    "/plugtrace diff",
                    "/plugtrace report upload",
                    "/plugtrace annotate ops <why this is expected or not>"
            );
            case UNKNOWN -> List.of("/plugtrace verify run", "/plugtrace status");
            default -> List.of("/plugtrace verify status", "/plugtrace status");
        };
    }

    public void enqueue(IssueEvent event) {
        if (!running.get()) {
            return;
        }
        if (!queue.offer(event)) {
            droppedEvents.incrementAndGet();
            logger.warning("PlugTrace event queue saturated; dropping low-value sample.");
        }
    }

    public Deployment currentDeployment() {
        return currentDeployment;
    }

    public Deployment baseline() {
        return baseline;
    }

    public List<Change> currentChanges() {
        return currentChanges;
    }

    public List<Issue> currentIssues() {
        return new ArrayList<>(issueBuffer.values());
    }

    public List<Suspect> currentSuspects() {
        return currentSuspects;
    }

    public String baselineDescription() {
        return baselineDescription;
    }

    public PlatformInfo platformInfo() {
        return platformInfo;
    }

    public String artifactId() {
        return artifactId;
    }

    public PlugDevIdentity plugDevIdentity() {
        return plugDevIdentity;
    }

    public boolean sparkDetected() {
        return sparkDetected;
    }

    public String sparkVersion() {
        return sparkVersion;
    }

    public String currentHealthName() {
        if (currentDeployment == null || currentDeployment.health() == null) {
            return "UNKNOWN";
        }
        return currentDeployment.health().name();
    }

    public long currentDeploymentSequence() {
        return currentDeployment == null ? 0L : currentDeployment.localSequence();
    }

    public String strongestSuspectLabel() {
        if (currentSuspects == null || currentSuspects.isEmpty()) {
            return "none";
        }
        Suspect top = currentSuspects.get(0);
        return top.componentKey() + " [" + top.band() + "]";
    }

    /** Soft PlaceholderAPI registration - safe to call after SERVER_LOAD. */
    public boolean registerPlaceholderApi(org.bukkit.plugin.java.JavaPlugin host) {
        return PlaceholderApiHook.tryRegister(host, this, logger);
    }

    public String sparkProfileUrl() {
        return sparkProfileUrl;
    }

    public List<Annotation> currentAnnotations() {
        return store.listAnnotations(currentDeployment.id());
    }

    public List<Deployment> listDeployments(int limit) {
        return store.listDeployments(nodeId, limit);
    }

    public void markHealth(DeploymentHealth health, String note) {
        List<String> reasons = note == null || note.isBlank()
                ? List.of("Marked manually.")
                : List.of(note);
        List<String> tags = new ArrayList<>(currentDeployment.tags());
        tags.removeIf(t -> t.equals("healthy") || t.equals("broken") || t.equals("degraded"));
        tags.add(health.name().toLowerCase(Locale.ROOT));
        currentDeployment = currentDeployment.withHealth(health, reasons).withTags(tags);
        store.markDeploymentHealth(currentDeployment.id(), health, reasons, tags);
    }

    public Annotation annotate(String category, String text, String actor, String link) {
        String normalized = category == null ? "other" : category.toLowerCase(Locale.ROOT);
        if (!ANNOTATION_CATEGORIES.contains(normalized)) {
            normalized = "other";
        }
        Annotation annotation = new Annotation(
                UUID.randomUUID().toString(),
                currentDeployment.id(),
                Instant.now(),
                actor == null ? "console" : actor,
                normalized,
                text == null ? "" : text,
                link
        );
        store.saveAnnotation(annotation);
        return annotation;
    }

    public void setSparkLink(String url) {
        this.sparkProfileUrl = url;
        annotate("spark", "Spark profile linked: " + url, "console", url);
    }

    public void recordHostedReport(String id, String shareUrl, String expiresAt, String deleteToken) {
        this.lastHostedReportId = id;
        this.lastHostedShareUrl = shareUrl;
        this.lastHostedExpiresAt = expiresAt;
        this.lastHostedDeleteToken = deleteToken;
        try {
            Path meta = dataFolder.resolve("reports").resolve("last-hosted.json");
            Files.createDirectories(meta.getParent());
            String json = "{"
                    + "\"id\":\"" + jsonEscape(id) + "\","
                    + "\"shareUrl\":\"" + jsonEscape(shareUrl) + "\","
                    + "\"expiresAt\":\"" + jsonEscape(expiresAt) + "\","
                    + "\"deleteToken\":\"" + jsonEscape(deleteToken) + "\","
                    + "\"deploymentSequence\":" + (currentDeployment == null ? 0 : currentDeployment.localSequence())
                    + "}\n";
            Files.writeString(meta, json);
        } catch (Exception e) {
            logger.warning("Could not persist hosted report metadata: " + e.getMessage());
        }
    }

    public String lastHostedShareUrl() {
        return lastHostedShareUrl;
    }

    private static String jsonEscape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public ReportService.ReportArtifacts generateReport() {
        return generateReport(Map.of("type", "deployment"), currentChanges, currentIssues(), currentSuspects);
    }

    public ReportService.ReportArtifacts generatePluginScopedReport(String pluginName) {
        String target = pluginName.toLowerCase(Locale.ROOT);
        ComponentSnapshot component = currentDeployment.components().stream()
                .filter(c -> c.identity().normalizedName().equalsIgnoreCase(pluginName))
                .findFirst()
                .orElse(null);
        List<String> deps = new ArrayList<>();
        if (component != null) {
            deps.addAll(component.identity().dependencies());
            deps.addAll(component.identity().softDependencies());
        }
        Set<String> related = deps.stream().map(d -> d.toLowerCase(Locale.ROOT)).collect(Collectors.toSet());
        related.add(target);

        List<Change> scopedChanges = currentChanges.stream()
                .filter(c -> {
                    String key = c.componentKey().toLowerCase(Locale.ROOT);
                    return related.stream().anyMatch(key::contains);
                })
                .toList();
        List<Issue> scopedIssues = currentIssues().stream()
                .filter(i -> i.ownershipCandidates().stream()
                        .anyMatch(o -> related.contains(o.toLowerCase(Locale.ROOT)))
                        || related.stream().anyMatch(r -> i.normalizedMessage().toLowerCase(Locale.ROOT).contains(r)))
                .toList();
        List<Suspect> scopedSuspects = currentSuspects.stream()
                .filter(s -> related.stream().anyMatch(r -> s.componentKey().toLowerCase(Locale.ROOT).contains(r)))
                .toList();
        if (scopedSuspects.isEmpty()) {
            scopedSuspects = attributionEngine.attribute(scopedIssues, scopedChanges);
        }

        Map<String, Object> scope = new LinkedHashMap<>();
        scope.put("type", "plugin");
        scope.put("name", pluginName);
        scope.put("dependencies", deps);
        return generateReport(scope, scopedChanges, scopedIssues, scopedSuspects);
    }

    private ReportService.ReportArtifacts generateReport(
            Map<String, Object> scope,
            List<Change> changes,
            List<Issue> issues,
            List<Suspect> suspects
    ) {
        recomputeSuspects();
        Map<String, Object> spark = new LinkedHashMap<>();
        spark.put("detected", sparkDetected);
        if (sparkVersion != null) {
            spark.put("version", sparkVersion);
        }
        if (sparkProfileUrl != null) {
            spark.put("profileUrl", sparkProfileUrl);
        }
        spark.put("note", sparkDetected
                ? "Spark is present; PlugTrace does not replace profiling."
                : "Spark not detected.");

        Map<String, Object> platform = new LinkedHashMap<>();
        platform.put("fork", platformInfo.forkFamily());
        platform.put("supportTier", platformInfo.supportTier());
        platform.put("artifact", platformInfo.artifact());
        platform.put("capabilities", CapabilityRegistry.forArtifact(artifactId).all().stream()
                .map(Enum::name).sorted().toList());
        if (platformInfo.migrateHint() != null) {
            platform.put("migrateHint", platformInfo.migrateHint());
        }

        Map<String, Object> release = plugDevIdentity == null ? Map.of() : plugDevIdentity.toMap();
        Map<String, Object> fields = new LinkedHashMap<>(safeFields);

        ReportRequest request = new ReportRequest(
                currentDeployment,
                baseline,
                changes,
                issues,
                suspects,
                currentAnnotations(),
                baselineDescription,
                spark,
                scope,
                platform,
                release,
                fields,
                null,
                currentVerification,
                currentIncidents(),
                privacyMode
        );
        ReportService.ReportArtifacts artifacts = reportService.generate(request);
        try {
            String base = "deployment-" + currentDeployment.localSequence();
            if ("plugin".equals(scope.get("type"))) {
                base = base + "-plugin-" + String.valueOf(scope.get("name")).replaceAll("[^A-Za-z0-9._-]", "_");
            }
            Path reports = dataFolder.resolve("reports");
            Files.writeString(reports.resolve(base + ".json"), artifacts.json());
            Files.writeString(reports.resolve(base + ".md"), artifacts.markdown());
            Files.writeString(reports.resolve(base + ".html"), artifacts.html());
            Files.writeString(reports.resolve(base + ".discord.txt"), artifacts.discord());
            Files.writeString(reports.resolve(base + ".github.md"), artifacts.github());
        } catch (Exception e) {
            throw new IllegalStateException("Unable to write report files", e);
        }
        return artifacts;
    }

    public List<String> selfcheck() {
        List<String> lines = new ArrayList<>();
        lines.add("integrity=" + store.integrityCheck());
        lines.add("deployments=" + store.countDeployments(nodeId) + " (retain max " + retentionDeployments + ")");
        lines.add("queueDepth=" + queue.size());
        lines.add("droppedEvents=" + droppedEvents.get());
        lines.add("lastSnapshotMs=" + lastSnapshotMillis);
        lines.add("lastSyncTickUs=" + lastSyncTickMicros);
        lines.add("syncTickP95Us=" + String.format(Locale.ROOT, "%.3f", syncTickP95Micros()));
        lines.add("dbBytes=" + fileSizeOrZero(dataFolder.resolve("plugtrace.db")));
        lines.add("retainedJarBytes=" + jarRetention.estimateRetainedBytes());
        lines.add("jarRetention=" + (jarRetention.enabled() ? "enabled" : "disabled"));
        lines.add("spark=" + (sparkDetected ? ("detected:" + sparkVersion) : "absent"));
        lines.add("noiseRules=" + noiseRules.suppressedFingerprints().size() + " fingerprints");
        lines.add("platform=" + platformInfo.forkFamily() + "/" + platformInfo.supportTier());
        lines.add("artifact=" + artifactId);
        lines.add("privacyMode=" + privacyMode);
        lines.add("plugdev=" + (plugDevIdentity == null
                ? "absent"
                : plugDevIdentity.shortCommit() + " " + Objects.toString(plugDevIdentity.projectName(), "")));
        lines.add("currentDeployment=#" + currentDeployment.localSequence());
        lines.add("safeFields=" + safeFields.size());
        lines.add("rawSamplesPerIssue=" + rawSamplesPerIssue);
        lines.add("scheduler=" + (scheduler == null ? "fallback" : (scheduler.isFolia() ? "folia" : "bukkit")));
        if (operatorConfig != null) {
            lines.add("verification.initialDelaySeconds=" + operatorConfig.initialDelaySeconds);
            lines.add("verification.observationMinutes=" + operatorConfig.observationMinutes);
            lines.add("web.bind=" + operatorConfig.webBind);
            lines.add("web.port=" + operatorConfig.webPort);
            lines.add("web.enabled=" + operatorConfig.webEnabled);
            lines.add("web.allowRemote=" + operatorConfig.webAllowRemote);
            lines.add("cloud.enabled=" + operatorConfig.cloudEnabled);
            lines.add("cloud.uploadUrl=" + operatorConfig.cloudUploadUrl);
            lines.add("cloud.viewerUrl=" + operatorConfig.cloudViewerUrl);
            lines.add("cloud.ttlDays=" + operatorConfig.cloudTtlDays);
        }
        return lines;
    }

    private static long fileSizeOrZero(Path path) {
        try {
            return Files.isRegularFile(path) ? Files.size(path) : 0L;
        } catch (Exception e) {
            return -1L;
        }
    }

    private void drainLoop() {
        while (running.get()) {
            try {
                IssueEvent event = queue.poll(250, TimeUnit.MILLISECONDS);
                if (event == null) {
                    continue;
                }
                ingest(event);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                logger.warning("PlugTrace worker error: " + e.getMessage());
            }
        }
    }

    private void ingest(IssueEvent event) {
        String fingerprint = fingerprintEngine.fingerprint(event);
        Instant now = event.eventAt();
        String truncatedStack = truncateSample(event.stackTrace());
        Issue existing = issueBuffer.get(fingerprint);
        if (existing == null) {
            List<String> samples = new ArrayList<>();
            samples.add(truncatedStack);
            rawSamples.put(fingerprint, samples);
            Issue issue = new Issue(
                    UUID.randomUUID().toString(),
                    fingerprint,
                    event.throwableType(),
                    fingerprintEngine.normalize(event.message()),
                    event.ownershipHints(),
                    now,
                    now,
                    IssueStatus.NEW,
                    event.severity(),
                    1,
                    joinSamples(samples),
                    RegressionClass.NONE
            );
            issueBuffer.put(fingerprint, issue);
            store.saveIssue(currentDeployment.id(), issue);
        } else {
            List<String> samples = rawSamples.computeIfAbsent(fingerprint, k -> new ArrayList<>());
            if (samples.size() < rawSamplesPerIssue && truncatedStack != null && !truncatedStack.isBlank()) {
                samples.add(truncatedStack);
            }
            Issue updated = existing.withCount(existing.occurrenceCount() + 1, now);
            if (samples.size() <= rawSamplesPerIssue) {
                updated = new Issue(
                        updated.id(),
                        updated.fingerprint(),
                        updated.normalizedType(),
                        updated.normalizedMessage(),
                        updated.ownershipCandidates(),
                        updated.firstSeenAt(),
                        updated.lastSeenAt(),
                        updated.status(),
                        updated.severity(),
                        updated.occurrenceCount(),
                        joinSamples(samples),
                        updated.regressionClass()
                );
            }
            issueBuffer.put(fingerprint, updated);
            store.saveIssue(currentDeployment.id(), updated);
        }
        recomputeSuspects();
        incidentEngine.openForRuntimeIssues(currentDeployment.id(), Instant.now(), currentIssues(),
                        store.listIncidents(currentDeployment.id(), 100))
                .ifPresent(store::saveIncident);
    }

    public List<String> resolveOwnership(String stackTrace, List<String> loggerHints) {
        StackOwnershipIndex index = ownershipIndex;
        if (index == StackOwnershipIndex.empty() && currentDeployment != null) {
            index = StackOwnershipIndex.build(currentDeployment.components(), WrapperRegistry.packaged());
            ownershipIndex = index;
        }
        return index.resolve(stackTrace, loggerHints);
    }

    private static String truncateSample(String stack) {
        if (stack == null || stack.isBlank()) {
            return "";
        }
        String[] lines = stack.split("\\R");
        int keep = Math.min(lines.length, 40);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keep; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        if (lines.length > 40) {
            sb.append("\n…");
        }
        return sb.toString();
    }

    private static String joinSamples(List<String> samples) {
        if (samples == null || samples.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < samples.size(); i++) {
            if (i > 0) {
                sb.append("\n--- sample ").append(i + 1).append(" ---\n");
            }
            sb.append(samples.get(i));
        }
        return sb.toString();
    }

    private void recomputeSuspects() {
        List<Issue> baselineIssues = baseline == null ? List.of() : store.listIssues(baseline.id());
        List<Issue> classified = regressionEngine.classify(
                currentIssues(), baselineIssues, currentChanges, noiseRules
        );
        issueBuffer.clear();
        for (Issue issue : classified) {
            issueBuffer.put(issue.fingerprint(), issue);
            store.saveIssue(currentDeployment.id(), issue);
        }
        currentSuspects = attributionEngine.attribute(
                classified.stream().filter(i -> i.status() != IssueStatus.EXPECTED).toList(),
                currentChanges
        );

        boolean hasNew = classified.stream().anyMatch(i ->
                i.regressionClass() == RegressionClass.NEW_ISSUE
                        || i.regressionClass() == RegressionClass.STARTUP_REGRESSION);
        if (hasNew && currentDeployment.health() == DeploymentHealth.UNKNOWN) {
            currentDeployment = currentDeployment.withHealth(
                    DeploymentHealth.DEGRADED,
                    List.of("New or startup regression detected versus baseline.")
            );
            store.saveDeployment(currentDeployment);
        }
    }

    private String loadOrCreateNodeId(org.bukkit.Server server) {
        Path idFile = dataFolder.resolve("node-id.txt");
        try {
            if (Files.exists(idFile)) {
                return Files.readString(idFile).trim();
            }
            String id = "node_" + Integer.toHexString(server.getPort()) + "_" + UUID.randomUUID().toString().substring(0, 8);
            Files.writeString(idFile, id);
            return id;
        } catch (Exception e) {
            return "node_" + UUID.randomUUID();
        }
    }

    @Override
    public synchronized void close() {
        running.set(false);
        PlugTraceAPI.bind(null);
        if (currentDeployment != null && currentDeployment.endedAt() == null) {
            currentDeployment = currentDeployment.withTermination(
                    Instant.now(), DeploymentLifecycle.STOPPED_CLEANLY, List.of());
            store.saveDeployment(currentDeployment);
        }
        // SchedulerFacade closed by plugin after service; store must close here.
        store.close();
    }

    public RestorePlan restorePreview() {
        lastRestorePlan = restoreService.preview(currentDeployment, baseline);
        return lastRestorePlan;
    }

    public RestorePlan restoreStage(boolean confirmed) throws Exception {
        RestoreService.assertStageConfirmed(confirmed);
        if (lastRestorePlan == null || lastRestorePlan.status() != RestorePlan.Status.PREVIEW) {
            lastRestorePlan = restoreService.preview(currentDeployment, baseline);
        }
        if (!lastRestorePlan.warnings().isEmpty()) {
            logger.warning("Restore warnings (" + lastRestorePlan.warnings().size() + ") - confirm acknowledges them:");
            for (String warning : lastRestorePlan.warnings()) {
                logger.warning("  ! " + warning);
            }
        }
        lastRestorePlan = restoreService.stage(lastRestorePlan);
        annotate("ops", "Restore staged toward baseline " + baselineDescription
                + " plan=" + lastRestorePlan.id(), "console", null);
        return lastRestorePlan;
    }

    /**
     * In-plugin finalize is refused while the server is running (JAR file locks).
     * Writes FINALIZE_PENDING and points operators at the offline tool.
     */
    public RestorePlan restoreFinalize() throws Exception {
        if (lastRestorePlan == null || lastRestorePlan.status() != RestorePlan.Status.STAGED) {
            // Try load latest staged from disk
            Path staged = RestoreJournalCodec.findLatestStagedPlan(dataFolder);
            if (staged != null) {
                lastRestorePlan = RestoreJournalCodec.readPlan(staged);
            }
        }
        if (lastRestorePlan == null || (lastRestorePlan.status() != RestorePlan.Status.STAGED
                && !hasStagedActionsOnDisk(lastRestorePlan))) {
            throw new IllegalStateException("No staged restore - run /plugtrace restore stage confirm first");
        }
        RestoreJournalCodec.writePlan(dataFolder, lastRestorePlan.withStatus(RestorePlan.Status.STAGED), "FINALIZE_PENDING");
        throw new IllegalStateException(
                "In-plugin finalize refused while the server is running (JAR locks). "
                        + "1) Stop the server cleanly. "
                        + "2) Offline finalize: java -jar core-domain/build/libs/core-domain-*.jar "
                        + dataFolder.toAbsolutePath()
                        + (lastRestorePlan != null ? " " + lastRestorePlan.id() : "")
                        + "  OR: ./gradlew :core-domain:finalizeRestore -PplugtraceData=" + dataFolder.toAbsolutePath()
                        + "  3) Start server → /plugtrace restore verify → /plugtrace restore complete"
        );
    }

    private static boolean hasStagedActionsOnDisk(RestorePlan plan) {
        if (plan == null) {
            return false;
        }
        for (RestorePlan.RestoreAction action : plan.actions()) {
            if (action.livePath() == null) {
                continue;
            }
            Path staged = Path.of(action.livePath()).resolveSibling(
                    Path.of(action.livePath()).getFileName().toString() + ".plugtrace-restore");
            if (Files.isRegularFile(staged)) {
                return true;
            }
        }
        return plan.status() == RestorePlan.Status.STAGED;
    }

    public RestorePlan restoreAbort() throws Exception {
        if (lastRestorePlan == null) {
            Path staged = RestoreJournalCodec.findLatestStagedPlan(dataFolder);
            if (staged != null) {
                lastRestorePlan = RestoreJournalCodec.readPlan(staged);
            }
        }
        if (lastRestorePlan == null) {
            throw new IllegalStateException("No restore plan to abort");
        }
        lastRestorePlan = restoreService.abortToOriginals(lastRestorePlan);
        annotate("ops", "Restore aborted; originals restored where backups existed", "console", null);
        return lastRestorePlan;
    }

    public List<String> restoreVerify() {
        List<String> evidence = restoreService.verify(currentDeployment, baseline);
        if (lastRestorePlan == null) {
            return evidence;
        }
        double before = issueRatePerMinute(store.listIssues(lastRestorePlan.targetDeploymentId()));
        double after = issueRatePerMinute(currentIssues());
        DeploymentVerification beforeVerification = store.findLatestVerification(lastRestorePlan.targetDeploymentId())
                .orElse(null);
        RecoveryVerification verification = recoveryVerificationEngine.evaluate(
                UUID.randomUUID().toString(), currentDeployment.id(), lastRestorePlan.id(), Instant.now(),
                before, after, beforeVerification, currentVerification);
        store.saveRecoveryVerification(verification);
        List<String> out = new ArrayList<>(evidence);
        out.add(verification.summary());
        return out;
    }

    private static double issueRatePerMinute(List<Issue> issues) {
        double total = 0.0;
        for (Issue issue : issues) {
            long seconds = Math.max(60L,
                    java.time.Duration.between(issue.firstSeenAt(), issue.lastSeenAt()).getSeconds());
            total += issue.occurrenceCount() / (seconds / 60.0);
        }
        return total;
    }

    public RestorePlan restoreComplete() throws Exception {
        Path marker = RestoreJournalCodec.pendingCompleteMarker(dataFolder);
        String planId = lastRestorePlan != null ? lastRestorePlan.id() : null;
        if (Files.isRegularFile(marker)) {
            planId = Files.readString(marker).trim();
        }
        if (planId == null || planId.isBlank()) {
            throw new IllegalStateException("No pending restore to complete - run offline finalize first");
        }
        List<String> tags = new ArrayList<>(currentDeployment.tags());
        if (!tags.contains("restored-from-baseline")) {
            tags.add("restored-from-baseline");
        }
        currentDeployment = currentDeployment.withTags(tags);
        store.saveDeployment(currentDeployment);
        annotate("ops", "Restore complete plan=" + planId
                + " - review /plugtrace verify; mark healthy when stable", "console", null);
        Files.deleteIfExists(marker);
        if (lastRestorePlan != null) {
            lastRestorePlan = lastRestorePlan.withStatus(RestorePlan.Status.APPLIED);
        }
        return lastRestorePlan;
    }

    /** Called on startup: remind operator if a restore was applied offline. */
    public void checkPendingRestoreComplete() {
        Path marker = RestoreJournalCodec.pendingCompleteMarker(dataFolder);
        if (Files.isRegularFile(marker)) {
            try {
                String planId = Files.readString(marker).trim();
                logger.info("Offline restore APPLIED (plan=" + planId
                        + "). Run /plugtrace restore verify then /plugtrace restore complete.");
            } catch (Exception e) {
                logger.warning("Could not read pending-complete marker: " + e.getMessage());
            }
        }
    }

    public RestorePlan lastRestorePlan() {
        return lastRestorePlan;
    }

    public Path dataFolder() {
        return dataFolder;
    }

    public long lastSnapshotMillis() {
        return lastSnapshotMillis;
    }

    public SchedulerFacade scheduler() {
        return scheduler;
    }
}
