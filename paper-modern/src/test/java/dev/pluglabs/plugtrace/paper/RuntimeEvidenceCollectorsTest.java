package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RuntimeEvidenceCollectorsTest {
    @TempDir
    Path tempDir;

    @Test
    void returnsOnlyCrashReportsWrittenDuringThePreviousDeployment() throws Exception {
        Path reports = Files.createDirectories(tempDir.resolve("crash-reports"));
        Path oldReport = Files.writeString(reports.resolve("old.txt"), "old");
        Path newReport = Files.writeString(reports.resolve("new.txt"), "new");
        Files.setLastModifiedTime(oldReport, FileTime.from(Instant.parse("2026-07-16T00:59:59Z")));
        Files.setLastModifiedTime(newReport, FileTime.from(Instant.parse("2026-07-16T01:00:30Z")));

        List<String> result = CrashReportScanner.findSince(
                tempDir, Instant.parse("2026-07-16T01:00:00Z"), Instant.parse("2026-07-16T01:01:00Z"));

        assertEquals(List.of("crash-reports/new.txt"), result);
    }

    @Test
    void readsAverageTickTimeWithoutDependingOnPaperAtCompileTime() {
        assertEquals(24.5, ServerMsptProbe.sample(new FakePaperServer()).orElseThrow());
        assertTrue(ServerMsptProbe.sample(new Object()).isEmpty());
    }

    public static final class FakePaperServer {
        public double getAverageTickTime() {
            return 24.5;
        }
    }
}
