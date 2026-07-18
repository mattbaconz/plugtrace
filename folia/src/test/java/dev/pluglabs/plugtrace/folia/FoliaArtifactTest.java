package dev.pluglabs.plugtrace.folia;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;

class FoliaArtifactTest {
    @Test void descriptorAndLocalWebArePackaged() throws Exception {
        String descriptor = resource("/plugin.yml");
        assertTrue(descriptor.contains("folia-supported: true"));
        assertTrue(descriptor.contains("version: '0.4.0'"));
        assertTrue(resource("/web/index.html").contains("PlugTrace Web"));
    }
    private static String resource(String name) throws Exception {
        try (var in = FoliaArtifactTest.class.getResourceAsStream(name)) {
            assertNotNull(in, name); return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
