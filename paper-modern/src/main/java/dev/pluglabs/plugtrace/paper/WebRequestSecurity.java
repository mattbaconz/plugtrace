package dev.pluglabs.plugtrace.paper;

import java.net.URI;

final class WebRequestSecurity {
    private WebRequestSecurity() {}

    static boolean sameOrigin(String origin, String host) {
        if (origin == null || origin.isBlank() || host == null || host.isBlank()) return false;
        try {
            URI parsed = URI.create(origin);
            String scheme = parsed.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) return false;
            if (parsed.getRawUserInfo() != null || parsed.getRawQuery() != null || parsed.getRawFragment() != null) {
                return false;
            }
            String path = parsed.getRawPath();
            if (path != null && !path.isEmpty() && !"/".equals(path)) return false;
            return host.equalsIgnoreCase(parsed.getRawAuthority());
        } catch (IllegalArgumentException ignored) {
            return false;
        }
    }
}
