package dev.pluglabs.plugtrace.domain;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.logging.Logger;
import java.util.stream.Stream;

/**
 * Retains prior plugin JAR bytes under PlugTrace data for Phase 5 restore.
 * Local-only; never exports premium contents.
 */
public final class JarRetentionService {
    private final Path retainRoot;
    private final long maxBytes;
    private final int maxVersionsPerComponent;
    private final boolean enabled;
    private final Logger logger;

    public JarRetentionService(Path dataFolder, long maxBytes, int maxVersionsPerComponent, Logger logger) {
        this(dataFolder, maxBytes, maxVersionsPerComponent, true, logger);
    }

    public JarRetentionService(
            Path dataFolder,
            long maxBytes,
            int maxVersionsPerComponent,
            boolean enabled,
            Logger logger
    ) {
        this.retainRoot = dataFolder.resolve("retained");
        this.maxBytes = Math.max(16L * 1024 * 1024, maxBytes);
        this.maxVersionsPerComponent = Math.max(1, maxVersionsPerComponent);
        this.enabled = enabled;
        this.logger = logger;
    }

    public boolean enabled() {
        return enabled;
    }

    public Path retainRoot() {
        return retainRoot;
    }

    public void retainIfPresent(ComponentSnapshot component) {
        if (!enabled) {
            return;
        }
        if (component == null || component.identity() == null) {
            return;
        }
        if (component.identity().type() != ComponentType.PLUGIN) {
            return;
        }
        String hash = component.identity().binaryHash();
        String path = component.jarPathForRestore();
        if (hash == null || hash.isBlank() || path == null || path.isBlank()) {
            return;
        }
        Path source = Path.of(path);
        if (!Files.isRegularFile(source)) {
            return;
        }
        try {
            String safeName = sanitize(component.identity().normalizedName());
            Path destDir = retainRoot.resolve(safeName);
            Files.createDirectories(destDir);
            Path dest = destDir.resolve(hash + ".jar");
            if (!Files.exists(dest)) {
                Files.copy(source, dest, StandardCopyOption.COPY_ATTRIBUTES);
            }
            pruneComponent(destDir);
            pruneGlobal();
        } catch (Exception e) {
            if (logger != null) {
                logger.warning("JAR retention skipped for " + component.identity().normalizedName() + ": " + e.getMessage());
            }
        }
    }

    public Path findRetained(String componentName, String sha256) {
        if (componentName == null || sha256 == null || sha256.isBlank()) {
            return null;
        }
        Path candidate = retainRoot.resolve(sanitize(componentName)).resolve(sha256 + ".jar");
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    /** Best-effort total bytes under retained/ for selfcheck performance block. */
    public long estimateRetainedBytes() {
        if (!Files.isDirectory(retainRoot)) {
            return 0L;
        }
        long total = 0L;
        try (Stream<Path> walk = Files.walk(retainRoot)) {
            for (Path p : walk.filter(f -> f.getFileName().toString().endsWith(".jar")).toList()) {
                total += Files.size(p);
            }
        } catch (IOException e) {
            return -1L;
        }
        return total;
    }

    private void pruneComponent(Path destDir) throws IOException {
        List<Path> jars;
        try (Stream<Path> stream = Files.list(destDir)) {
            jars = stream.filter(p -> p.getFileName().toString().endsWith(".jar"))
                    .sorted(Comparator.comparingLong((Path p) -> {
                        try {
                            return Files.getLastModifiedTime(p).toMillis();
                        } catch (IOException e) {
                            return 0L;
                        }
                    }).reversed())
                    .toList();
        }
        for (int i = maxVersionsPerComponent; i < jars.size(); i++) {
            Files.deleteIfExists(jars.get(i));
        }
    }

    private void pruneGlobal() throws IOException {
        if (!Files.isDirectory(retainRoot)) {
            return;
        }
        List<Path> all = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(retainRoot)) {
            walk.filter(p -> p.getFileName().toString().endsWith(".jar")).forEach(all::add);
        }
        long total = 0;
        for (Path jar : all) {
            total += Files.size(jar);
        }
        if (total <= maxBytes) {
            return;
        }
        all.sort(Comparator.comparingLong((Path p) -> {
            try {
                return Files.getLastModifiedTime(p).toMillis();
            } catch (IOException e) {
                return 0L;
            }
        }));
        for (Path jar : all) {
            if (total <= maxBytes) {
                break;
            }
            long size = Files.size(jar);
            Files.deleteIfExists(jar);
            total -= size;
        }
    }

    private static String sanitize(String name) {
        return name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9._-]", "_");
    }
}
