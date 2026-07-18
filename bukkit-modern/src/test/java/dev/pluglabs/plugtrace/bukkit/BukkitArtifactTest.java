package dev.pluglabs.plugtrace.bukkit;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class BukkitArtifactTest {
    @Test void descriptorIsExplicitlyNonFoliaAndLocalWebIsPackaged() throws Exception {
        String descriptor = resource("/plugin.yml");
        assertFalse(descriptor.contains("folia-supported: true"));
        assertTrue(descriptor.contains("version: '0.5.0'"));
        assertTrue(descriptor.contains("api-version: '1.20'"));
        assertTrue(resource("/web/index.html").contains("PlugTrace Web"));
    }

    @Test void mainArtifactUsesJava17Bytecode() throws Exception {
        assertJava17("/dev/pluglabs/plugtrace/paper/PlugTracePlugin.class");
        assertJava17("/dev/pluglabs/plugtrace/platform/SchedulerFacade.class");
        assertJava17("/dev/pluglabs/plugtrace/domain/Deployment.class");
    }

    private static void assertJava17(String resource) throws Exception {
        try (var in = BukkitArtifactTest.class.getResourceAsStream(resource)) {
            assertNotNull(in);
            byte[] header = in.readNBytes(8);
            int major = ((header[6] & 0xff) << 8) | (header[7] & 0xff);
            assertEquals(61, major, resource + " must use Java 17 bytecode");
        }
    }
    private static String resource(String name) throws Exception {
        try (var in = BukkitArtifactTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name); return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
