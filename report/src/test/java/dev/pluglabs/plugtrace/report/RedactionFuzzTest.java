package dev.pluglabs.plugtrace.report;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RedactionFuzzTest {
    @Test
    void removesCommonSecretsIdentifiersPathsAndControlCharacters() {
        List<String> sensitive = List.of(
                "password: hunter2",
                "Authorization=Bearer abc.def-123",
                "webhook=https://discord.com/api/webhooks/123456/token-value",
                "player=550e8400-e29b-41d4-a716-446655440000",
                "remote=10.20.30.40:25565",
                "windows=C:\\Users\\matt\\server\\plugins",
                "unix=/home/matt/server/plugins",
                "control=hello\u0000world\u001b[31m"
        );
        RedactionService service = new RedactionService();

        String output = service.redact(String.join("\n", sensitive));

        assertFalse(output.contains("hunter2"));
        assertFalse(output.contains("abc.def-123"));
        assertFalse(output.contains("discord.com/api/webhooks"));
        assertFalse(output.contains("550e8400-e29b-41d4-a716-446655440000"));
        assertFalse(output.contains("10.20.30.40"));
        assertFalse(output.contains("C:\\Users\\matt"));
        assertFalse(output.contains("/home/matt"));
        assertFalse(output.contains("\u0000"));
        assertFalse(output.contains("\u001b"));
        assertTrue(output.contains("<redacted>"));
    }
}
