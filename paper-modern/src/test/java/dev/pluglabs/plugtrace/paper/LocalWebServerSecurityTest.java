package dev.pluglabs.plugtrace.paper;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalWebServerSecurityTest {
    @TempDir Path temp;

    @Test void refusesRemoteBindingWithoutExplicitOptIn() {
        var web = new LocalWebServer(null, Logger.getAnonymousLogger(), temp, "0.0.0.0", 9465, false);
        assertThrows(IOException.class, web::start);
    }

    @Test void failsClosedWhenPortIsUnavailable() throws Exception {
        try (ServerSocket occupied = new ServerSocket(0)) {
            var web = new LocalWebServer(null, Logger.getAnonymousLogger(), temp,
                    "127.0.0.1", occupied.getLocalPort(), false);
            assertThrows(IOException.class, web::start);
        }
    }

    @Test void enforcesTokenScopeOriginHeadersCorsAndTraversalOverHttp() throws Exception {
        var web = new LocalWebServer(null, Logger.getAnonymousLogger(), temp,
                "127.0.0.1", 0, false);
        web.start();
        try {
            String read = web.createToken("read-test", WebTokenStore.Scope.READ);
            String admin = web.createToken("admin-test", WebTokenStore.Scope.ADMIN);
            HttpClient client = HttpClient.newHttpClient();

            HttpResponse<String> asset = client.send(HttpRequest.newBuilder(
                            URI.create(web.address() + "/index.html")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, asset.statusCode());
            assertTrue(asset.headers().firstValue("Content-Security-Policy").orElse("")
                    .contains("frame-ancestors 'none'"));
            assertFalse(asset.headers().firstValue("Access-Control-Allow-Origin").isPresent());

            HttpResponse<String> readCannotWrite = client.send(HttpRequest.newBuilder(
                            URI.create(web.address() + "/api/v1/checkpoints"))
                    .header("Authorization", "Bearer " + read).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(401, readCannotWrite.statusCode());

            HttpResponse<String> missingOrigin = client.send(HttpRequest.newBuilder(
                            URI.create(web.address() + "/api/v1/checkpoints"))
                    .header("Authorization", "Bearer " + admin).POST(HttpRequest.BodyPublishers.noBody()).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(403, missingOrigin.statusCode());

            HttpResponse<String> traversal = client.send(HttpRequest.newBuilder(
                            URI.create(web.address() + "/..%2Fplugin.yml")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            assertTrue(traversal.statusCode() == 400 || traversal.statusCode() == 404);
        } finally {
            web.close();
        }
    }
}
