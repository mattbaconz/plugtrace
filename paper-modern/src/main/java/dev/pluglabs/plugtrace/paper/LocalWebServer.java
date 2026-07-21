package dev.pluglabs.plugtrace.paper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import dev.pluglabs.plugtrace.domain.Checkpoint;
import dev.pluglabs.plugtrace.domain.DeploymentVerification;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

final class LocalWebServer implements AutoCloseable {
    private static final String CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; connect-src 'self'; object-src 'none'; base-uri 'none'; frame-ancestors 'none'";

    private final PlugTraceService service;
    private final Logger logger;
    private final String bind;
    private final int port;
    private final boolean allowRemote;
    private final WebTokenStore tokens;
    private final ObjectMapper mapper = new ObjectMapper()
            .setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
    private HttpServer server;
    private ExecutorService executor;
    private ScheduledExecutorService eventPublisher;
    private final SseBroker events = new SseBroker(16);

    LocalWebServer(PlugTraceService service, Logger logger, Path dataFolder,
                   String bind, int port, boolean allowRemote) {
        this.service = service;
        this.logger = logger;
        this.bind = bind == null || bind.isBlank() ? "127.0.0.1" : bind;
        this.port = port;
        this.allowRemote = allowRemote;
        this.tokens = new WebTokenStore(dataFolder.resolve("web-tokens.properties"));
    }

    synchronized void start() throws IOException {
        if (!isLoopback(bind) && !allowRemote) {
            throw new IOException("Remote web bind refused; set web.allowRemote=true explicitly");
        }
        server = HttpServer.create(new InetSocketAddress(bind, port), 32);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "plugtrace-web");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.createContext("/api/v1", this::handleApi);
        server.createContext("/", this::handleAsset);
        server.start();
        eventPublisher = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "plugtrace-web-events");
            thread.setDaemon(true);
            return thread;
        });
        eventPublisher.scheduleAtFixedRate(this::publishStatus, 5, 5, TimeUnit.SECONDS);
        if (!isLoopback(bind)) logger.warning("PlugTrace Web is remotely bound. Put it behind a TLS reverse proxy.");
    }

    boolean running() { return server != null; }
    String address() {
        int actualPort = server == null ? port : server.getAddress().getPort();
        return "http://" + bind + ":" + actualPort;
    }

    String createToken(String name, WebTokenStore.Scope scope) throws Exception { return tokens.create(name, scope); }
    boolean revokeToken(String name) throws Exception { return tokens.revoke(name); }

    private void handleApi(HttpExchange exchange) throws IOException {
        secure(exchange);
        String path = exchange.getRequestURI().getPath().substring("/api/v1".length());
        boolean write = !"GET".equalsIgnoreCase(exchange.getRequestMethod());
        if (!authorized(exchange, write ? WebTokenStore.Scope.ADMIN : WebTokenStore.Scope.READ)) {
            json(exchange, 401, Map.of("error", "unauthorized"));
            return;
        }
        if (write && !sameOrigin(exchange)) {
            json(exchange, 403, Map.of("error", "origin rejected"));
            return;
        }
        try {
            switch (path) {
                case "/status" -> json(exchange, 200, status());
                case "/deployments" -> json(exchange, 200,
                        service.listDeployments(WebPagination.from(exchange.getRequestURI()).limit()));
                case "/diff" -> json(exchange, 200, Map.of(
                        "baseline", service.baselineDescription(),
                        "changes", service.currentChanges(),
                        "suspects", service.currentSuspects()
                ));
                case "/issues" -> json(exchange, 200, service.currentIssues());
                case "/incidents" -> json(exchange, 200, service.currentIncidents().stream()
                        .limit(WebPagination.from(exchange.getRequestURI()).limit()).toList());
                case "/checkpoints" -> {
                    if (write) {
                        Checkpoint checkpoint = service.createCheckpoint("Web checkpoint", "web");
                        json(exchange, 201, checkpoint);
                    } else json(exchange, 200,
                            service.checkpoints(WebPagination.from(exchange.getRequestURI()).limit()));
                }
                case "/verification" -> {
                    if (write) {
                        service.requestVerification(false);
                        json(exchange, 202, Map.of("status", "started"));
                    }
                    else json(exchange, 200, service.currentVerification() == null ? Map.of() : service.currentVerification());
                }
                case "/restore-plans" -> json(exchange, 200, service.restorePreview());
                case "/restore-plans/stage" -> {
                    if (!write) json(exchange, 405, Map.of("error", "method not allowed"));
                    else json(exchange, 202, service.restoreStage(true));
                }
                case "/reports" -> {
                    if (write) json(exchange, 201, service.generateReport());
                    else json(exchange, 200, Map.of(
                            "formats", java.util.List.of("json", "markdown", "html", "discord", "github"),
                            "schemaVersion", "1.0.0",
                            "automaticUpload", false));
                }
                case "/events" -> event(exchange);
                default -> json(exchange, 404, Map.of("error", "not found"));
            }
        } catch (Exception e) {
            logger.warning("PlugTrace Web request failed: " + e.getMessage());
            json(exchange, 500, Map.of("error", "request failed"));
        }
    }

    private Map<String, Object> status() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("deployment", service.currentDeployment());
        out.put("baseline", service.baselineDescription());
        out.put("changes", service.currentChanges().size());
        out.put("issues", service.currentIssues().size());
        out.put("verification", service.currentVerification());
        out.put("platform", service.platformInfo());
        out.put("ritual", service.ritualStatus());
        out.put("localOnly", isLoopback(bind));
        out.put("web", Map.of(
                "bind", bind,
                "port", port,
                "allowRemote", allowRemote,
                "address", address()
        ));
        out.put("config", service.effectiveConfig());
        return out;
    }

    private void handleAsset(HttpExchange exchange) throws IOException {
        secure(exchange);
        String requested = exchange.getRequestURI().getPath();
        if (requested.contains("..")) { exchange.sendResponseHeaders(400, -1); return; }
        if (requested.equals("/")) requested = "/index.html";
        try (var in = getClass().getResourceAsStream("/web" + requested)) {
            if (in == null) { exchange.sendResponseHeaders(404, -1); return; }
            byte[] bytes = in.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType(requested));
            exchange.sendResponseHeaders(200, bytes.length);
            exchange.getResponseBody().write(bytes);
        } finally {
            exchange.close();
        }
    }

    private boolean authorized(HttpExchange exchange, WebTokenStore.Scope scope) {
        String header = exchange.getRequestHeaders().getFirst("Authorization");
        return header != null && header.startsWith("Bearer ") && tokens.verify(header.substring(7), scope);
    }

    private boolean sameOrigin(HttpExchange exchange) {
        String origin = exchange.getRequestHeaders().getFirst("Origin");
        String host = exchange.getRequestHeaders().getFirst("Host");
        return WebRequestSecurity.sameOrigin(origin, host);
    }

    private void event(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.sendResponseHeaders(200, 0);
        if (!events.subscribe(exchange.getResponseBody())) {
            exchange.close();
            return;
        }
        events.publish("status", mapper.writeValueAsString(status()));
    }

    private void publishStatus() {
        if (events.subscriberCount() == 0 || service == null) return;
        try {
            events.publish("status", mapper.writeValueAsString(status()));
        } catch (Exception e) {
            logger.fine("Unable to publish web status event: " + e.getMessage());
        }
    }

    private void json(HttpExchange exchange, int code, Object value) throws IOException {
        byte[] bytes = mapper.writeValueAsBytes(value);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(code, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private static void secure(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Content-Security-Policy", CSP);
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
    }

    private static String contentType(String path) {
        if (path.endsWith(".js")) return "text/javascript; charset=utf-8";
        if (path.endsWith(".css")) return "text/css; charset=utf-8";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "text/html; charset=utf-8";
    }

    private static boolean isLoopback(String host) {
        return "127.0.0.1".equals(host) || "localhost".equalsIgnoreCase(host) || "::1".equals(host);
    }

    @Override public synchronized void close() {
        if (server != null) server.stop(0);
        if (eventPublisher != null) eventPublisher.shutdownNow();
        events.close();
        if (executor != null) executor.shutdownNow();
        server = null;
    }
}
