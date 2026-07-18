package dev.pluglabs.plugtrace.paper;

import java.net.URI;

record WebPagination(int limit) {
    private static final int DEFAULT_LIMIT = 25;
    private static final int MAX_LIMIT = 100;

    static WebPagination from(URI uri) {
        String query = uri == null ? null : uri.getRawQuery();
        if (query == null || query.isBlank()) return new WebPagination(DEFAULT_LIMIT);
        for (String pair : query.split("&")) {
            String[] parts = pair.split("=", 2);
            if (parts.length == 2 && "limit".equals(parts[0])) {
                try {
                    int parsed = Integer.parseInt(parts[1]);
                    return new WebPagination(Math.max(1, Math.min(MAX_LIMIT, parsed)));
                } catch (NumberFormatException ignored) {
                    return new WebPagination(DEFAULT_LIMIT);
                }
            }
        }
        return new WebPagination(DEFAULT_LIMIT);
    }
}
