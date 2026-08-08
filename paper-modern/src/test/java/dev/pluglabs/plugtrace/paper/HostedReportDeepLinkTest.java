package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class HostedReportDeepLinkTest {
    @Test
    void failingAddsChecksLensBeforeFragment() {
        String in = "https://plugtrace.dev/r/abc#k=secretkey";
        String out = HostedReportClient.withViewerDeepLink(in, "FAILING");
        assertEquals("https://plugtrace.dev/r/abc?lens=checks#k=secretkey", out);
    }

    @Test
    void degradedUsesSuspectsLens() {
        String out = HostedReportClient.withViewerDeepLink(
                "https://plugtrace.dev/r/x#k=k", "DEGRADED");
        assertTrue(out.contains("?lens=suspects#"));
    }

    @Test
    void healthyLeavesUrlAlone() {
        String in = "https://plugtrace.dev/r/abc#k=secretkey";
        assertEquals(in, HostedReportClient.withViewerDeepLink(in, "HEALTHY"));
    }
}
