package dev.pluglabs.plugtrace.domain;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Offline finalize entry — run with the Minecraft server stopped so JAR locks are free.
 * Usage: OfflineRestoreFinalizer &lt;path-to-plugins/PlugTrace&gt; [planId]
 */
public final class OfflineRestoreFinalizer {
    private OfflineRestoreFinalizer() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: OfflineRestoreFinalizer <plugtrace-data-folder> [planId]");
            System.err.println("Example: OfflineRestoreFinalizer .plugdev/run/plugins/PlugTrace");
            System.exit(2);
        }
        Path dataFolder = Path.of(args[0]).toAbsolutePath().normalize();
        if (!Files.isDirectory(dataFolder)) {
            System.err.println("Not a directory: " + dataFolder);
            System.exit(1);
        }
        RestorePlan applied = finalizeOffline(dataFolder, args.length >= 2 ? args[1] : null);
        System.out.println("Restore APPLIED: " + applied.id() + " (" + applied.actions().size() + " actions)");
        System.out.println("Start the server, then run: /plugtrace restore verify && /plugtrace restore complete");
    }

    public static RestorePlan finalizeOffline(Path dataFolder, String planId) throws Exception {
        Path planFile;
        if (planId != null && !planId.isBlank()) {
            planFile = RestoreJournalCodec.planFile(dataFolder, planId);
        } else {
            planFile = RestoreJournalCodec.findLatestStagedPlan(dataFolder);
        }
        if (planFile == null || !Files.isRegularFile(planFile)) {
            throw new IllegalStateException("No STAGED restore plan found under " + dataFolder.resolve("restore-journal"));
        }
        RestorePlan plan = RestoreJournalCodec.readPlan(planFile);
        if (plan.status() != RestorePlan.Status.STAGED && plan.status() != RestorePlan.Status.PREVIEW) {
            // Allow re-read of STAGED from file even if status parse quirks
            if (!Files.readString(planFile).contains("STAGED")) {
                throw new IllegalStateException("Plan is not STAGED: " + plan.status());
            }
            plan = new RestorePlan(
                    plan.id(),
                    plan.targetDeploymentId(),
                    plan.baselineDeploymentId(),
                    plan.createdAt(),
                    RestorePlan.Status.STAGED,
                    plan.actions(),
                    plan.warnings(),
                    plan.journalPath() == null ? planFile.toString() : plan.journalPath()
            );
        }
        JarRetentionService retention = new JarRetentionService(dataFolder, 512L * 1024 * 1024, 3, null);
        RestoreService service = new RestoreService(dataFolder, retention);
        RestorePlan applied = service.finalizeStaged(plan);
        Files.writeString(RestoreJournalCodec.pendingCompleteMarker(dataFolder), applied.id());
        return applied;
    }
}
