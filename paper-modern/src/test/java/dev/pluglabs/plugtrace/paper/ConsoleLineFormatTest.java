package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConsoleLineFormatTest {
    @Test
    void compactLabelMiddleEllipsis() {
        String path = "Essentials:userdata/82163dde-4b00-3a81-8a05-550dbbe994ad.yml";
        String compact = ConsoleLineFormat.compactLabel(path, 40);
        assertEquals(40, compact.length());
        assertTrue(compact.contains("…"));
        assertTrue(compact.startsWith("Essentials:"));
        assertTrue(compact.endsWith(".yml"));
    }

    @Test
    void shortenExplanationDropsDuplicatePath() {
        String component = "Essentials:userdata/82163dde-4b00-3a81-8a05-550dbbe994ad.yml";
        assertEquals(
                "Config appeared",
                ConsoleLineFormat.shortenExplanation(
                        "Config appeared: userdata/82163dde-4b00-3a81-8a05-550dbbe994ad.yml",
                        component));
    }

    @Test
    void wrapPlainBreaksOnPathSeparators() {
        String longPath = "CONFIG_HASH_CHANGED Essentials:userdata/"
                + "82163dde-4b00-3a81-8a05-550dbbe994ad.yml - Config appeared";
        List<String> chunks = ConsoleLineFormat.wrapPlain(longPath, 48);
        assertTrue(chunks.size() >= 2);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= 48, chunk);
        }
        assertEquals(longPath.replace(" ", ""), String.join("", chunks).replace(" ", ""));
    }
}
