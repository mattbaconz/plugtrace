package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class FingerprintEngineTest {
    private final FingerprintEngine engine = new FingerprintEngine();

    @Test
    void groupsEventsDifferingOnlyByDynamicIds() {
        IssueEvent a = event(
                "Player 550e8400-e29b-41d4-a716-446655440000 failed at 2026-07-13T12:00:00Z from 127.0.0.1",
                "at com.example.Shop.onClick(Shop.java:42)"
        );
        IssueEvent b = event(
                "Player 11111111-2222-3333-4444-555555555555 failed at 2026-07-13T18:30:11Z from 10.0.0.5",
                "at com.example.Shop.onClick(Shop.java:99)"
        );

        assertEquals(engine.fingerprint(a), engine.fingerprint(b));
    }

    @Test
    void differentFramesProduceDifferentFingerprints() {
        IssueEvent a = event("boom", "at com.example.A.run(A.java:1)");
        IssueEvent b = event("boom", "at com.example.B.run(B.java:1)");
        assertNotEquals(engine.fingerprint(a), engine.fingerprint(b));
    }

    @Test
    void rootCauseChainAndExecutionContextArePartOfTheFingerprint() {
        IssueEvent database = new IssueEvent(
                null, Instant.parse("2026-07-13T00:00:00Z"), "dep-1", "command", "error",
                "java.lang.RuntimeException", "operation failed",
                "java.lang.RuntimeException: operation failed\n"
                        + "\tat com.example.Shop.run(Shop.java:1)\n"
                        + "Caused by: java.sql.SQLException: access denied\n"
                        + "\tat com.example.Db.open(Db.java:8)",
                List.of("frame:Shop"), "Server thread");
        IssueEvent serializer = new IssueEvent(
                null, Instant.parse("2026-07-13T00:00:00Z"), "dep-1", "command", "error",
                "java.lang.RuntimeException", "operation failed",
                "java.lang.RuntimeException: operation failed\n"
                        + "\tat com.example.Shop.run(Shop.java:1)\n"
                        + "Caused by: java.lang.IllegalArgumentException: bad item\n"
                        + "\tat com.example.Serializer.read(Serializer.java:8)",
                List.of("frame:Shop"), "Server thread");
        IssueEvent scheduler = new IssueEvent(
                null, Instant.parse("2026-07-13T00:00:00Z"), "dep-1", "scheduler", "error",
                "java.lang.RuntimeException", "operation failed", database.stackTrace(),
                List.of("frame:Shop"), "Server thread");

        assertNotEquals(engine.fingerprint(database), engine.fingerprint(serializer));
        assertNotEquals(engine.fingerprint(database), engine.fingerprint(scheduler));
    }

    private static IssueEvent event(String message, String frame) {
        return new IssueEvent(
                null,
                Instant.parse("2026-07-13T00:00:00Z"),
                "dep-1",
                "logger",
                "error",
                "java.lang.RuntimeException",
                message,
                "java.lang.RuntimeException: x\n\tat " + frame,
                List.of("Shop"),
                "Server thread"
        );
    }
}
