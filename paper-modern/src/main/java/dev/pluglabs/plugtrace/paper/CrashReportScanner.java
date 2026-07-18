package dev.pluglabs.plugtrace.paper;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/** Best-effort, bounded crash reference discovery; report contents are never ingested here. */
final class CrashReportScanner {
    private static final int MAX_REFERENCES = 20;

    private CrashReportScanner() {}

    static List<String> findSince(Path serverRoot, Instant startedAt, Instant detectedAt) {
        if (serverRoot == null || startedAt == null || detectedAt == null) {
            return List.of();
        }
        Path reports = serverRoot.resolve("crash-reports");
        if (!Files.isDirectory(reports)) {
            return List.of();
        }
        try (var files = Files.list(reports)) {
            return files.filter(Files::isRegularFile)
                    .filter(file -> withinWindow(file, startedAt, detectedAt))
                    .sorted(Comparator.comparing(Path::toString))
                    .limit(MAX_REFERENCES)
                    .map(serverRoot::relativize)
                    .map(path -> path.toString().replace('\\', '/'))
                    .toList();
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private static boolean withinWindow(Path file, Instant startedAt, Instant detectedAt) {
        try {
            Instant modified = Files.getLastModifiedTime(file).toInstant();
            return !modified.isBefore(startedAt) && !modified.isAfter(detectedAt);
        } catch (Exception ignored) {
            return false;
        }
    }
}
