package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class ArchitectureBoundaryTest {
    @Test
    void coreDomainHasNoMinecraftImports() throws IOException {
        Path root = Path.of("src/main/java");
        try (var paths = Files.walk(root)) {
            paths.filter(p -> p.toString().endsWith(".java")).forEach(path -> {
                try {
                    for (String line : Files.readAllLines(path)) {
                        String trimmed = line.trim();
                        if (!trimmed.startsWith("import ")) {
                            continue;
                        }
                        assertFalse(trimmed.contains("org.bukkit"), path + " imports bukkit: " + trimmed);
                        assertFalse(trimmed.contains("io.papermc"), path + " imports paper: " + trimmed);
                        assertFalse(trimmed.contains("org.spigotmc"), path + " imports spigot: " + trimmed);
                        assertFalse(trimmed.contains("net.minecraft"), path + " imports nms: " + trimmed);
                    }
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            });
        }
    }

    @Test
    void stateFingerprintIsStableForIdenticalState() {
        Deployment a = sample(1);
        Deployment b = sample(2);
        assertEquals(StateFingerprint.compute(a), StateFingerprint.compute(b));
    }

    private static Deployment sample(long seq) {
        return Deployment.builder()
                .id("x-" + seq)
                .localSequence(seq)
                .nodeId("n")
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVendor("Temurin")
                .javaVersion("21")
                .components(List.of(new ComponentSnapshot(
                        new ComponentIdentity(
                                ComponentType.PLUGIN, "Demo", "1.0", "abc",
                                List.of(), List.of(), List.of(), "Main", "1.21"
                        ),
                        "plugins/Demo.jar", 10, true, true, null
                )))
                .build();
    }
}
