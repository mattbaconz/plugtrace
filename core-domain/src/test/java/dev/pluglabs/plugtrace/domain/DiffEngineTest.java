package dev.pluglabs.plugtrace.domain;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiffEngineTest {
    private final DiffEngine engine = new DiffEngine();

    @Test
    void detectsSameVersionBinaryChange() {
        Deployment baseline = deployment(1, List.of(plugin("Shop", "2.4.0", "aaa", true)));
        Deployment current = deployment(2, List.of(plugin("Shop", "2.4.0", "bbb", true)));

        List<Change> changes = engine.diff(baseline, current);

        assertTrue(changes.stream().anyMatch(c -> c.type() == ChangeType.BINARY_CHANGED_SAME_VERSION));
        assertEquals("PLUGIN:shop", changes.stream()
                .filter(c -> c.type() == ChangeType.BINARY_CHANGED_SAME_VERSION)
                .findFirst()
                .orElseThrow()
                .componentKey());
    }

    @Test
    void detectsAddedAndRemovedPlugins() {
        Deployment baseline = deployment(1, List.of(plugin("A", "1.0", "h1", true)));
        Deployment current = deployment(2, List.of(plugin("B", "1.0", "h2", true)));

        List<Change> changes = engine.diff(baseline, current);

        assertTrue(changes.stream().anyMatch(c -> c.type() == ChangeType.COMPONENT_ADDED));
        assertTrue(changes.stream().anyMatch(c -> c.type() == ChangeType.COMPONENT_REMOVED));
    }

    @Test
    void noBaselineYieldsExplicitMessage() {
        Deployment current = deployment(1, List.of());
        List<Change> changes = engine.diff(null, current);
        assertFalse(changes.isEmpty());
        assertTrue(changes.get(0).explanation().contains("No baseline"));
    }

    private static Deployment deployment(long seq, List<ComponentSnapshot> components) {
        return Deployment.builder()
                .id("d-" + seq)
                .localSequence(seq)
                .nodeId("node-1")
                .startedAt(Instant.parse("2026-07-13T00:00:00Z"))
                .lifecycle(DeploymentLifecycle.OBSERVING)
                .health(DeploymentHealth.HEALTHY)
                .serverImplementation("Paper")
                .minecraftVersion("1.21.4")
                .javaVersion("21")
                .javaVendor("Temurin")
                .components(components)
                .build();
    }

    private static ComponentSnapshot plugin(String name, String version, String hash, boolean enabled) {
        return new ComponentSnapshot(
                new ComponentIdentity(
                        ComponentType.PLUGIN, name, version, hash,
                        List.of("test"), List.of(), List.of(), "Main", "1.21"
                ),
                "plugins/" + name + ".jar",
                100,
                true,
                enabled,
                null
        );
    }
}
