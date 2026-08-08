package dev.pluglabs.plugtrace.paper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.logging.Logger;

/**
 * Opt-in Discord webhook POST for FAILING/DEGRADED ritual digests.
 * Never uploads PlugTrace reports; body must already be redacted/summary-only.
 */
final class DiscordWebhookNotifier {
    private static final int LIMIT = 1900;
    private static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .build();

    private DiscordWebhookNotifier() {
    }

    static void postAsync(String webhookUrl, String plainContent, Logger logger) {
        if (webhookUrl == null || webhookUrl.isBlank() || plainContent == null || plainContent.isBlank()) {
            return;
        }
        String body = plainContent.length() <= LIMIT ? plainContent : plainContent.substring(0, LIMIT - 1) + "…";
        String json = "{\"content\":" + jsonString(body) + "}";
        Thread t = new Thread(() -> {
            try {
                HttpRequest req = HttpRequest.newBuilder(URI.create(webhookUrl.trim()))
                        .timeout(Duration.ofSeconds(12))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8))
                        .build();
                HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    if (logger != null) {
                        logger.warning("PlugTrace Discord webhook HTTP " + resp.statusCode());
                    }
                }
            } catch (Exception e) {
                if (logger != null) {
                    logger.warning("PlugTrace Discord webhook failed: " + e.getClass().getSimpleName()
                            + " — core PlugTrace continues.");
                }
            }
        }, "plugtrace-discord-notify");
        t.setDaemon(true);
        t.start();
    }

    private static String jsonString(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 16);
        sb.append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '\\' -> sb.append("\\\\");
                case '"' -> sb.append("\\\"");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
        return sb.toString();
    }
}
