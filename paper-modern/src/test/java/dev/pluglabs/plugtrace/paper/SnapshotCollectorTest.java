package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnapshotCollectorTest {
    @Test
    void paperArtifactPackagesTheLocalDashboard() throws Exception {
        try (var in = getClass().getResourceAsStream("/web/index.html")) {
            assertNotNull(in);
            assertTrue(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)
                    .contains("PlugTrace Web"));
        }
    }
    @Test
    void countsValueFreeStructuralKeys() {
        List<String> lines = List.of(
                "database:",
                "  host: localhost",
                "  password: secret",
                "features:",
                "  shop: true",
                "# ignored: comment",
                "- list item"
        );
        assertEquals(5, SnapshotCollector.countStructuralKeys(lines));
    }
}
