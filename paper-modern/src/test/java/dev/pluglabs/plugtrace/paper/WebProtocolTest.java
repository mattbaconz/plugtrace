package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WebProtocolTest {
    @Test
    void paginationIsBoundedAndRejectsInvalidInput() {
        assertEquals(25, WebPagination.from(URI.create("http://localhost/api/v1/deployments")).limit());
        assertEquals(100, WebPagination.from(URI.create("http://localhost/api/v1/deployments?limit=999")).limit());
        assertEquals(1, WebPagination.from(URI.create("http://localhost/api/v1/deployments?limit=-2")).limit());
        assertEquals(25, WebPagination.from(URI.create("http://localhost/api/v1/deployments?limit=nope")).limit());
    }

    @Test
    void writeOriginsMustBePresentAndExactlyMatchTheRequestHost() {
        assertTrue(WebRequestSecurity.sameOrigin("http://127.0.0.1:9465", "127.0.0.1:9465"));
        assertTrue(WebRequestSecurity.sameOrigin("https://trace.example.test", "trace.example.test"));
        assertFalse(WebRequestSecurity.sameOrigin(null, "127.0.0.1:9465"));
        assertFalse(WebRequestSecurity.sameOrigin("https://evil.example", "trace.example.test"));
        assertFalse(WebRequestSecurity.sameOrigin("https://trace.example.test.evil", "trace.example.test"));
    }

    @Test
    void eventBrokerKeepsAStreamOpenAcrossMultipleEvents() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        SseBroker broker = new SseBroker(2);
        assertTrue(broker.subscribe(output));

        broker.publish("status", "{\"health\":\"OBSERVING\"}");
        broker.publish("status", "{\"health\":\"HEALTHY\"}");

        String events = output.toString(StandardCharsets.UTF_8);
        assertTrue(events.contains("OBSERVING"));
        assertTrue(events.contains("HEALTHY"));
        assertEquals(1, broker.subscriberCount());
        broker.close();
    }
}
