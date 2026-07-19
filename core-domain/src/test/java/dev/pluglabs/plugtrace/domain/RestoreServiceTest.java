package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RestoreServiceTest {
    @Test
    void stageRequiresConfirm() {
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> RestoreService.assertStageConfirmed(false)
        );
        assertTrue(ex.getMessage().contains("confirm"));
        RestoreService.assertStageConfirmed(true);
    }

    @Test
    void interruptedRestoreKeepsOriginals() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-restore");
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);

        Path live = plugins.resolve("Demo.jar");
        Path oldRetained = root.resolve("retained").resolve("demo").resolve("aaaa".repeat(8) + ".jar");
        Files.createDirectories(oldRetained.getParent());
        Files.writeString(live, "CURRENT-BINARY");
        Files.writeString(oldRetained, "BASELINE-BINARY");

        String currentHash = Sha256Hasher.hashFile(live);
        String baselineHash = Sha256Hasher.hashFile(oldRetained);

        Path retainNamed = root.resolve("retained").resolve("demo").resolve(baselineHash + ".jar");
        Files.createDirectories(retainNamed.getParent());
        Files.copy(oldRetained, retainNamed);

        JarRetentionService retention = new JarRetentionService(root, 64L * 1024 * 1024, 3, null);
        RestoreService restore = new RestoreService(root, retention);

        ComponentSnapshot currentComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Demo", "2.0", currentHash,
                        List.of(), List.of(), List.of(), "demo.Main", "1.21"),
                "plugins/Demo.jar", 10, true, true, null, live.toString()
        );
        ComponentSnapshot baselineComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Demo", "1.0", baselineHash,
                        List.of(), List.of(), List.of(), "demo.Main", "1.21"),
                "plugins/Demo.jar", 10, true, true, null, live.toString()
        );

        Deployment current = Deployment.builder()
                .id("cur")
                .localSequence(2)
                .nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.DEGRADED)
                .components(List.of(currentComp))
                .stateFingerprint("c")
                .build();
        Deployment baseline = Deployment.builder()
                .id("base")
                .localSequence(1)
                .nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.HEALTHY)
                .components(List.of(baselineComp))
                .stateFingerprint("b")
                .build();

        RestorePlan preview = restore.preview(current, baseline);
        assertEquals(1, preview.actions().size());
        assertEquals("REPLACE_JAR", preview.actions().get(0).kind());

        RestoreService.assertStageConfirmed(true);
        RestorePlan staged = restore.stage(preview);
        assertEquals(RestorePlan.Status.STAGED, staged.status());
        assertTrue(Files.isRegularFile(live.resolveSibling("Demo.jar.plugtrace-original")));
        assertTrue(Files.isRegularFile(live.resolveSibling("Demo.jar.plugtrace-restore")));
        assertEquals("CURRENT-BINARY", Files.readString(live));
        assertTrue(Files.isRegularFile(RestoreJournalCodec.planFile(root, staged.id())));

        RestorePlan aborted = restore.abortToOriginals(staged);
        assertEquals(RestorePlan.Status.REVERTED, aborted.status());
        assertEquals("CURRENT-BINARY", Files.readString(live));
        assertTrue(Files.notExists(live.resolveSibling("Demo.jar.plugtrace-restore")));
    }

    @Test
    void stageThenFinalizeReplacesLiveJar() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-restore2");
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        Path live = plugins.resolve("Shop.jar");
        Files.writeString(live, "NEW");
        String currentHash = Sha256Hasher.hashFile(live);

        Path retainDir = root.resolve("retained").resolve("shop");
        Files.createDirectories(retainDir);
        Path baselineJar = retainDir.resolve("tmp.jar");
        Files.writeString(baselineJar, "OLD");
        String baselineHash = Sha256Hasher.hashFile(baselineJar);
        Path named = retainDir.resolve(baselineHash + ".jar");
        Files.move(baselineJar, named);

        JarRetentionService retention = new JarRetentionService(root, 64L * 1024 * 1024, 3, null);
        RestoreService restore = new RestoreService(root, retention);

        ComponentSnapshot currentComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Shop", "2", currentHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Shop.jar", 3, true, true, null, live.toString()
        );
        ComponentSnapshot baselineComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Shop", "1", baselineHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Shop.jar", 3, true, true, null, live.toString()
        );
        Deployment current = Deployment.builder().id("c").localSequence(2).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.UNKNOWN)
                .components(List.of(currentComp)).stateFingerprint("x").build();
        Deployment baseline = Deployment.builder().id("b").localSequence(1).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.HEALTHY)
                .components(List.of(baselineComp)).stateFingerprint("y").build();

        RestorePlan plan = restore.finalizeStaged(restore.stage(restore.preview(current, baseline)));
        assertEquals(RestorePlan.Status.APPLIED, plan.status());
        assertEquals("OLD", Files.readString(live));
        assertTrue(Files.isRegularFile(RestoreJournalCodec.pendingCompleteMarker(root)));
    }

    @Test
    void offlineFinalizeAppliesStagedPlan() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-offline");
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        Path live = plugins.resolve("Eco.jar");
        Files.writeString(live, "LIVE");
        String currentHash = Sha256Hasher.hashFile(live);

        Path retainDir = root.resolve("retained").resolve("eco");
        Files.createDirectories(retainDir);
        Path baselineJar = retainDir.resolve("tmp.jar");
        Files.writeString(baselineJar, "BASE");
        String baselineHash = Sha256Hasher.hashFile(baselineJar);
        Files.move(baselineJar, retainDir.resolve(baselineHash + ".jar"));

        JarRetentionService retention = new JarRetentionService(root, 64L * 1024 * 1024, 3, null);
        RestoreService restore = new RestoreService(root, retention);

        ComponentSnapshot currentComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Eco", "2", currentHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Eco.jar", 3, true, true, null, live.toString()
        );
        ComponentSnapshot baselineComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Eco", "1", baselineHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Eco.jar", 3, true, true, null, live.toString()
        );
        Deployment current = Deployment.builder().id("c").localSequence(2).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.UNKNOWN)
                .components(List.of(currentComp)).stateFingerprint("x").build();
        Deployment baseline = Deployment.builder().id("b").localSequence(1).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.HEALTHY)
                .components(List.of(baselineComp)).stateFingerprint("y").build();

        RestorePlan staged = restore.stage(restore.preview(current, baseline));
        assertEquals("LIVE", Files.readString(live));

        RestorePlan applied = OfflineRestoreFinalizer.finalizeOffline(root, staged.id());
        assertEquals(RestorePlan.Status.APPLIED, applied.status());
        assertEquals("BASE", Files.readString(live));
        assertTrue(Files.isRegularFile(RestoreJournalCodec.pendingCompleteMarker(root)));
        assertEquals(staged.id(), Files.readString(RestoreJournalCodec.pendingCompleteMarker(root)).trim());
    }

    @Test
    void stageInterruptAfterOriginalKeepsLiveAndAllowsAbort() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-restore-stage-kill");
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        Path live = plugins.resolve("Demo.jar");
        Files.writeString(live, "CURRENT-BINARY");
        String currentHash = Sha256Hasher.hashFile(live);

        Path retainDir = root.resolve("retained").resolve("demo");
        Files.createDirectories(retainDir);
        Path baselineJar = retainDir.resolve("tmp.jar");
        Files.writeString(baselineJar, "BASELINE-BINARY");
        String baselineHash = Sha256Hasher.hashFile(baselineJar);
        Files.move(baselineJar, retainDir.resolve(baselineHash + ".jar"));

        RestoreService.BinaryIo killAfterOriginal = new RestoreService.BinaryIo() {
            private int copies;

            @Override
            public void copy(Path source, Path target, java.nio.file.CopyOption... options) throws java.io.IOException {
                Files.copy(source, target, options);
                copies++;
                if (copies == 1) {
                    throw new java.io.IOException("simulated JVM kill mid-stage after original backup");
                }
            }
        };

        JarRetentionService retention = new JarRetentionService(root, 64L * 1024 * 1024, 3, null);
        RestoreService restore = new RestoreService(root, retention, killAfterOriginal);

        ComponentSnapshot currentComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Demo", "2.0", currentHash,
                        List.of(), List.of(), List.of(), "demo.Main", "1.21"),
                "plugins/Demo.jar", 10, true, true, null, live.toString()
        );
        ComponentSnapshot baselineComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Demo", "1.0", baselineHash,
                        List.of(), List.of(), List.of(), "demo.Main", "1.21"),
                "plugins/Demo.jar", 10, true, true, null, live.toString()
        );
        Deployment current = Deployment.builder().id("cur").localSequence(2).nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.DEGRADED)
                .components(List.of(currentComp)).stateFingerprint("c").build();
        Deployment baseline = Deployment.builder().id("base").localSequence(1).nodeId("n1")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.HEALTHY)
                .components(List.of(baselineComp)).stateFingerprint("b").build();

        RestorePlan preview = restore.preview(current, baseline);
        assertThrows(java.io.IOException.class, () -> restore.stage(preview));

        Path original = live.resolveSibling("Demo.jar.plugtrace-original");
        assertTrue(Files.isRegularFile(original));
        assertEquals("CURRENT-BINARY", Files.readString(live));
        assertEquals("CURRENT-BINARY", Files.readString(original));
        assertTrue(Files.notExists(live.resolveSibling("Demo.jar.plugtrace-restore")));

        // Recover with default IO using a fresh service (same journal paths via re-stage after abort pattern)
        RestoreService recovery = new RestoreService(root, retention);
        RestorePlan staged = recovery.stage(preview);
        RestorePlan aborted = recovery.abortToOriginals(staged);
        assertEquals(RestorePlan.Status.REVERTED, aborted.status());
        assertEquals("CURRENT-BINARY", Files.readString(live));
        assertTrue(Files.notExists(live.resolveSibling("Demo.jar.plugtrace-restore")));
    }

    @Test
    void replaceInterruptKeepsOriginalAndAbortRestores() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-restore-replace-kill");
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        Path live = plugins.resolve("Shop.jar");
        Files.writeString(live, "NEW");
        String currentHash = Sha256Hasher.hashFile(live);

        Path retainDir = root.resolve("retained").resolve("shop");
        Files.createDirectories(retainDir);
        Path baselineJar = retainDir.resolve("tmp.jar");
        Files.writeString(baselineJar, "OLD");
        String baselineHash = Sha256Hasher.hashFile(baselineJar);
        Files.move(baselineJar, retainDir.resolve(baselineHash + ".jar"));

        RestoreService.BinaryIo killOnMove = new RestoreService.BinaryIo() {
            @Override
            public void copy(Path source, Path target, java.nio.file.CopyOption... options) throws java.io.IOException {
                Files.copy(source, target, options);
            }

            @Override
            public void move(Path source, Path target, java.nio.file.CopyOption... options) throws java.io.IOException {
                throw new java.io.IOException("simulated JVM kill mid-replace");
            }
        };

        JarRetentionService retention = new JarRetentionService(root, 64L * 1024 * 1024, 3, null);
        RestoreService restore = new RestoreService(root, retention, killOnMove);

        ComponentSnapshot currentComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Shop", "2", currentHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Shop.jar", 3, true, true, null, live.toString()
        );
        ComponentSnapshot baselineComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Shop", "1", baselineHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Shop.jar", 3, true, true, null, live.toString()
        );
        Deployment current = Deployment.builder().id("c").localSequence(2).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.UNKNOWN)
                .components(List.of(currentComp)).stateFingerprint("x").build();
        Deployment baseline = Deployment.builder().id("b").localSequence(1).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.HEALTHY)
                .components(List.of(baselineComp)).stateFingerprint("y").build();

        RestorePlan staged = new RestoreService(root, retention).stage(restore.preview(current, baseline));
        assertTrue(Files.isRegularFile(live.resolveSibling("Shop.jar.plugtrace-original")));
        assertThrows(java.io.IOException.class, () -> restore.finalizeStaged(staged));
        assertEquals("NEW", Files.readString(live));
        assertTrue(Files.isRegularFile(live.resolveSibling("Shop.jar.plugtrace-original")));
        assertTrue(Files.isRegularFile(live.resolveSibling("Shop.jar.plugtrace-restore")));

        RestorePlan aborted = new RestoreService(root, retention).abortToOriginals(staged);
        assertEquals(RestorePlan.Status.REVERTED, aborted.status());
        assertEquals("NEW", Files.readString(live));
        assertTrue(Files.notExists(live.resolveSibling("Shop.jar.plugtrace-restore")));
    }

    @Test
    void failedRestartAfterFinalizeKeepsOriginalsForAbort() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-restore-corrupt");
        Path plugins = root.resolve("plugins");
        Files.createDirectories(plugins);
        Path live = plugins.resolve("Eco.jar");
        Files.writeString(live, "LIVE");
        String currentHash = Sha256Hasher.hashFile(live);

        Path retainDir = root.resolve("retained").resolve("eco");
        Files.createDirectories(retainDir);
        Path baselineJar = retainDir.resolve("tmp.jar");
        Files.writeString(baselineJar, "BASE");
        String baselineHash = Sha256Hasher.hashFile(baselineJar);
        Files.move(baselineJar, retainDir.resolve(baselineHash + ".jar"));

        JarRetentionService retention = new JarRetentionService(root, 64L * 1024 * 1024, 3, null);
        RestoreService restore = new RestoreService(root, retention);

        ComponentSnapshot currentComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Eco", "2", currentHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Eco.jar", 3, true, true, null, live.toString()
        );
        ComponentSnapshot baselineComp = new ComponentSnapshot(
                new ComponentIdentity(ComponentType.PLUGIN, "Eco", "1", baselineHash,
                        List.of(), List.of(), List.of(), null, null),
                "plugins/Eco.jar", 3, true, true, null, live.toString()
        );
        Deployment current = Deployment.builder().id("c").localSequence(2).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.UNKNOWN)
                .components(List.of(currentComp)).stateFingerprint("x").build();
        Deployment baseline = Deployment.builder().id("b").localSequence(1).nodeId("n")
                .lifecycle(DeploymentLifecycle.OBSERVING).health(DeploymentHealth.HEALTHY)
                .components(List.of(baselineComp)).stateFingerprint("y").build();

        RestorePlan applied = restore.finalizeStaged(restore.stage(restore.preview(current, baseline)));
        assertEquals("BASE", Files.readString(live));
        Path original = live.resolveSibling("Eco.jar.plugtrace-original");
        assertTrue(Files.isRegularFile(original));
        assertEquals("LIVE", Files.readString(original));

        // Simulate corrupt post-finalize restart
        Files.writeString(live, "CORRUPT");
        RestorePlan aborted = restore.abortToOriginals(applied);
        assertEquals(RestorePlan.Status.REVERTED, aborted.status());
        assertEquals("LIVE", Files.readString(live));
    }
}
