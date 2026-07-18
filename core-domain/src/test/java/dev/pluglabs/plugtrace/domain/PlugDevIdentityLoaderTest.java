package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlugDevIdentityLoaderTest {
    @Test
    void parsesFixtureIdentity() throws Exception {
        Path file = Files.createTempFile("plugdev-identity", ".json");
        Files.writeString(file, """
                {
                  "schemaVersion": "1",
                  "gitCommit": "abcdef1234567890",
                  "gitDirty": false,
                  "buildSystem": "gradle",
                  "buildTask": "shadowJar",
                  "artifactHash": "deadbeef",
                  "projectName": "DemoShop",
                  "sessionId": "sess-1",
                  "plugdevVersion": "0.11.0",
                  "recordedAt": "2026-07-14T00:00:00Z"
                }
                """);

        Optional<PlugDevIdentity> loaded = PlugDevIdentityLoader.tryLoad(file);
        assertTrue(loaded.isPresent());
        PlugDevIdentity identity = loaded.get();
        assertEquals("abcdef1234567890", identity.gitCommit());
        assertEquals("abcdef12", identity.shortCommit());
        assertEquals("DemoShop", identity.projectName());
        assertEquals("deadbeef", identity.artifactHash());
        assertTrue(identity.toMap().containsKey("sessionId"));
    }

    @Test
    void searchesDataFolderFirst() throws Exception {
        Path root = Files.createTempDirectory("plugtrace-id");
        Path data = root.resolve("plugins").resolve("PlugTrace");
        Files.createDirectories(data);
        Files.writeString(data.resolve("plugdev-identity.json"), """
                {"schemaVersion":"1","gitCommit":"from-data","gitDirty":false,"projectName":"A"}
                """);
        Files.writeString(root.resolve("plugdev-identity.json"), """
                {"schemaVersion":"1","gitCommit":"from-root","gitDirty":false,"projectName":"B"}
                """);

        Optional<PlugDevIdentity> loaded = PlugDevIdentityLoader.load(data, root);
        assertTrue(loaded.isPresent());
        assertEquals("from-data", loaded.get().gitCommit());
    }
}
