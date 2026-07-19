package dev.pluglabs.plugtrace.domain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Phase 5 restore planner with journaled apply for interrupted-restore safety.
 * Never deletes originals until replacement succeeds; journal tracks stages.
 */
public final class RestoreService {
    private final Path dataFolder;
    private final JarRetentionService retention;
    private final BinaryIo binaryIo;

    /** File operations used by stage/finalize — injectable for interrupt-safety tests. */
    @FunctionalInterface
    public interface BinaryIo {
        void copy(Path source, Path target, java.nio.file.CopyOption... options) throws IOException;

        default void move(Path source, Path target, java.nio.file.CopyOption... options) throws IOException {
            Files.move(source, target, options);
        }
    }

    private static final BinaryIo DEFAULT_IO = new BinaryIo() {
        @Override
        public void copy(Path source, Path target, java.nio.file.CopyOption... options) throws IOException {
            Files.copy(source, target, options);
        }
    };

    public RestoreService(Path dataFolder, JarRetentionService retention) {
        this(dataFolder, retention, DEFAULT_IO);
    }

    public RestoreService(Path dataFolder, JarRetentionService retention, BinaryIo binaryIo) {
        this.dataFolder = dataFolder;
        this.retention = retention;
        this.binaryIo = binaryIo == null ? DEFAULT_IO : binaryIo;
    }

    /** Stage gate: operators must pass confirm / --confirm after reviewing preview warnings. */
    public static void assertStageConfirmed(boolean confirmed) {
        if (!confirmed) {
            throw new IllegalStateException(
                    "Staging requires confirmation. Review warnings, then: /plugtrace restore stage confirm"
                            + " — originals will be copied to *.plugtrace-original before any replace."
            );
        }
    }

    public RestorePlan preview(Deployment current, Deployment baseline) {
        List<RestorePlan.RestoreAction> actions = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        if (baseline == null) {
            warnings.add("No baseline deployment — cannot build restore plan.");
            return new RestorePlan(
                    UUID.randomUUID().toString(),
                    current == null ? null : current.id(),
                    null,
                    Instant.now(),
                    RestorePlan.Status.PREVIEW,
                    List.of(),
                    warnings,
                    null
            );
        }
        Map<String, ComponentSnapshot> currentByKey = index(current);
        Map<String, ComponentSnapshot> baselineByKey = index(baseline);

        for (Map.Entry<String, ComponentSnapshot> entry : baselineByKey.entrySet()) {
            String key = entry.getKey();
            ComponentSnapshot want = entry.getValue();
            ComponentSnapshot have = currentByKey.get(key);
            if (have != null && hashesEqual(have.identity().binaryHash(), want.identity().binaryHash())) {
                continue;
            }
            Path retained = retention.findRetained(want.identity().normalizedName(), want.identity().binaryHash());
            if (retained == null) {
                warnings.add("No retained JAR for " + key + " @" + shortHash(want.identity().binaryHash())
                        + " — rebuild/reinstall required.");
                actions.add(new RestorePlan.RestoreAction(
                        key,
                        "MISSING_BINARY",
                        have == null ? null : have.identity().binaryHash(),
                        want.identity().binaryHash(),
                        null,
                        want.jarPathForRestore(),
                        "Retained binary unavailable"
                ));
                continue;
            }
            if (have != null
                    && !want.identity().declaredVersion().equals(have.identity().declaredVersion())) {
                warnings.add("Version change " + key + ": " + have.identity().declaredVersion()
                        + " → " + want.identity().declaredVersion() + " (possible migration/downgrade risk)");
            }
            String livePath = have != null ? have.jarPathForRestore() : want.jarPathForRestore();
            actions.add(new RestorePlan.RestoreAction(
                    key,
                    "REPLACE_JAR",
                    have == null ? null : have.identity().binaryHash(),
                    want.identity().binaryHash(),
                    retained.toString(),
                    livePath,
                    "Replace live JAR with retained baseline binary"
            ));
        }

        for (String key : currentByKey.keySet()) {
            if (!baselineByKey.containsKey(key)) {
                warnings.add("Plugin " + key + " is present now but absent in baseline — will not auto-delete.");
            }
        }

        return new RestorePlan(
                UUID.randomUUID().toString(),
                current.id(),
                baseline.id(),
                Instant.now(),
                RestorePlan.Status.PREVIEW,
                actions,
                warnings,
                null
        );
    }

    /**
     * Stages restore: copies retained JARs beside live paths as {@code *.plugtrace-restore},
     * writes journal. Operator must stop server cleanly then call {@link #finalizeStaged}.
     * Originals are never deleted here.
     */
    public RestorePlan stage(RestorePlan plan) throws IOException {
        Path journalDir = dataFolder.resolve("restore-journal");
        Files.createDirectories(journalDir);
        Path journal = journalDir.resolve(plan.id() + ".json");
        List<RestorePlan.RestoreAction> staged = new ArrayList<>();
        for (RestorePlan.RestoreAction action : plan.actions()) {
            if (!"REPLACE_JAR".equals(action.kind())) {
                staged.add(action);
                continue;
            }
            Path retained = Path.of(action.retainedPath());
            Path live = Path.of(action.livePath());
            if (!Files.isRegularFile(retained)) {
                throw new IOException("Retained JAR missing: " + retained);
            }
            Path stagedPath = live.resolveSibling(live.getFileName().toString() + ".plugtrace-restore");
            Path backupPath = live.resolveSibling(live.getFileName().toString() + ".plugtrace-original");
            if (Files.isRegularFile(live) && !Files.exists(backupPath)) {
                binaryIo.copy(live, backupPath, StandardCopyOption.COPY_ATTRIBUTES);
            }
            binaryIo.copy(retained, stagedPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.COPY_ATTRIBUTES);
            staged.add(new RestorePlan.RestoreAction(
                    action.componentKey(),
                    action.kind(),
                    action.fromHash(),
                    action.toHash(),
                    action.retainedPath(),
                    action.livePath(),
                    "Staged at " + stagedPath + "; original backup " + backupPath
            ));
        }
        RestorePlan next = new RestorePlan(
                plan.id(),
                plan.targetDeploymentId(),
                plan.baselineDeploymentId(),
                plan.createdAt(),
                RestorePlan.Status.STAGED,
                staged,
                plan.warnings(),
                journal.toString()
        );
        RestoreJournalCodec.writePlan(dataFolder, next, "STAGED");
        return next;
    }

    /**
     * Atomically swaps staged files into place. Safe if interrupted: originals kept as
     * {@code *.plugtrace-original}; incomplete swaps leave {@code *.plugtrace-restore}.
     * Prefer {@link OfflineRestoreFinalizer} with the server stopped.
     */
    public RestorePlan finalizeStaged(RestorePlan plan) throws IOException {
        Path journal = plan.journalPath() == null
                ? dataFolder.resolve("restore-journal").resolve(plan.id() + ".json")
                : Path.of(plan.journalPath());
        writeJournal(journal, plan, "FINALIZING");
        for (RestorePlan.RestoreAction action : plan.actions()) {
            if (!"REPLACE_JAR".equals(action.kind()) || action.livePath() == null) {
                continue;
            }
            Path live = Path.of(action.livePath());
            Path stagedPath = live.resolveSibling(live.getFileName().toString() + ".plugtrace-restore");
            if (!Files.isRegularFile(stagedPath)) {
                continue;
            }
            try {
                binaryIo.move(stagedPath, live, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException atomicFailed) {
                binaryIo.move(stagedPath, live, StandardCopyOption.REPLACE_EXISTING);
            }
        }
        RestorePlan applied = plan.withStatus(RestorePlan.Status.APPLIED);
        writeJournal(journal, applied, "APPLIED");
        RestoreJournalCodec.writePlan(dataFolder, applied, "APPLIED");
        Files.writeString(RestoreJournalCodec.pendingCompleteMarker(dataFolder), applied.id());
        return applied;
    }

    /** Restores {@code *.plugtrace-original} if present (abort / interrupted recovery). */
    public RestorePlan abortToOriginals(RestorePlan plan) throws IOException {
        for (RestorePlan.RestoreAction action : plan.actions()) {
            if (action.livePath() == null) {
                continue;
            }
            Path live = Path.of(action.livePath());
            Path backup = live.resolveSibling(live.getFileName().toString() + ".plugtrace-original");
            Path staged = live.resolveSibling(live.getFileName().toString() + ".plugtrace-restore");
            Files.deleteIfExists(staged);
            if (Files.isRegularFile(backup)) {
                try {
                    binaryIo.move(backup, live, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                } catch (IOException e) {
                    binaryIo.move(backup, live, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
        RestorePlan reverted = plan.withStatus(RestorePlan.Status.REVERTED);
        if (plan.journalPath() != null) {
            writeJournal(Path.of(plan.journalPath()), reverted, "REVERTED");
        }
        RestoreJournalCodec.writePlan(dataFolder, reverted, "REVERTED");
        Files.deleteIfExists(RestoreJournalCodec.pendingCompleteMarker(dataFolder));
        return reverted;
    }

    public List<String> verify(Deployment current, Deployment baseline) {
        List<String> lines = new ArrayList<>();
        if (baseline == null || current == null) {
            lines.add("verify=skip (missing deployment)");
            return lines;
        }
        Map<String, ComponentSnapshot> currentByKey = index(current);
        Map<String, ComponentSnapshot> baselineByKey = index(baseline);
        int ok = 0;
        int mismatch = 0;
        for (Map.Entry<String, ComponentSnapshot> entry : baselineByKey.entrySet()) {
            ComponentSnapshot have = currentByKey.get(entry.getKey());
            if (have != null && hashesEqual(have.identity().binaryHash(), entry.getValue().identity().binaryHash())) {
                ok++;
            } else {
                mismatch++;
                lines.add("mismatch=" + entry.getKey());
            }
        }
        lines.add(0, "verify ok=" + ok + " mismatch=" + mismatch);
        return lines;
    }

    private void writeJournal(Path journal, RestorePlan plan, String stage) throws IOException {
        Files.createDirectories(journal.getParent());
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"id\": \"").append(esc(plan.id())).append("\",\n");
        sb.append("  \"stage\": \"").append(esc(stage)).append("\",\n");
        sb.append("  \"status\": \"").append(plan.status().name()).append("\",\n");
        sb.append("  \"targetDeploymentId\": \"").append(esc(plan.targetDeploymentId())).append("\",\n");
        sb.append("  \"baselineDeploymentId\": \"").append(esc(plan.baselineDeploymentId())).append("\",\n");
        sb.append("  \"writtenAt\": \"").append(Instant.now()).append("\",\n");
        sb.append("  \"actionCount\": ").append(plan.actions().size()).append("\n");
        sb.append("}\n");
        Files.writeString(journal, sb.toString(), StandardCharsets.UTF_8);
        Files.writeString(journal.resolveSibling(plan.id() + ".stage"), stage, StandardCharsets.UTF_8);
    }

    private static Map<String, ComponentSnapshot> index(Deployment deployment) {
        Map<String, ComponentSnapshot> map = new LinkedHashMap<>();
        if (deployment == null) {
            return map;
        }
        for (ComponentSnapshot component : deployment.components()) {
            if (component.identity().type() != ComponentType.PLUGIN) {
                continue;
            }
            map.put(component.identity().identityKey(), component);
        }
        return map;
    }

    private static boolean hashesEqual(String a, String b) {
        if (a == null || b == null || a.isBlank() || b.isBlank()) {
            return false;
        }
        return a.equalsIgnoreCase(b);
    }

    private static String shortHash(String hash) {
        if (hash == null || hash.length() < 8) {
            return String.valueOf(hash);
        }
        return hash.substring(0, 8);
    }

    private static String esc(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
