package dev.pluglabs.plugtrace.api;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VerificationApiTypesTest {
    @Test
    void definitionAndResultAreSafeImmutableValues() throws Exception {
        VerificationCheckDefinition definition = new VerificationCheckDefinition(
                "AuctionHouse", "database", "Database connection",
                VerificationCriticality.CRITICAL, VerificationExecution.ASYNC, Duration.ofSeconds(5));
        VerificationCheck check = context -> CompletableFuture.completedFuture(
                VerificationResult.pass("Database connected", Map.of("schema", 7)));

        VerificationResult result = check.run(new VerificationContext("deployment-1", false)).toCompletableFuture().get();

        assertEquals("AuctionHouse:database", definition.qualifiedId());
        assertEquals(VerificationStatus.PASS, result.status());
        assertEquals(7, result.safeDetails().get("schema"));
    }

    @Test
    void migrationCarriesRollbackSafety() {
        MigrationRecord migration = new MigrationRecord(
                "AuctionHouse", "6", "7", "Database schema migrated", RollbackSafety.UNSAFE);
        assertEquals(RollbackSafety.UNSAFE, migration.rollbackSafety());
    }
}
