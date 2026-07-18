package dev.pluglabs.plugtrace.paper;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Properties;

final class WebTokenStore {
    enum Scope { READ, ADMIN }

    private final Path file;
    private final SecureRandom random = new SecureRandom();

    WebTokenStore(Path file) {
        this.file = file;
    }

    synchronized String create(String name, Scope scope) throws Exception {
        String safeName = name == null || name.isBlank() ? "token" : name.replaceAll("[^A-Za-z0-9._-]", "_");
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Properties props = load();
        props.setProperty(safeName, scope.name() + ":" + hash(token));
        save(props);
        return token;
    }

    synchronized boolean verify(String token, Scope required) {
        if (token == null || token.isBlank()) return false;
        try {
            byte[] presented = hash(token).getBytes(StandardCharsets.US_ASCII);
            Properties props = load();
            for (String stored : props.stringPropertyNames().stream().map(props::getProperty).toList()) {
                String[] parts = stored.split(":", 2);
                if (parts.length != 2) continue;
                Scope actual = Scope.valueOf(parts[0]);
                boolean allowed = required == Scope.READ || actual == Scope.ADMIN;
                if (allowed && MessageDigest.isEqual(presented, parts[1].getBytes(StandardCharsets.US_ASCII))) return true;
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    synchronized boolean revoke(String name) throws Exception {
        Properties props = load();
        Object removed = props.remove(name);
        save(props);
        return removed != null;
    }

    private Properties load() throws Exception {
        Properties props = new Properties();
        if (Files.isRegularFile(file)) {
            try (InputStream in = Files.newInputStream(file)) { props.load(in); }
        }
        return props;
    }

    private void save(Properties props) throws Exception {
        if (file.getParent() != null) Files.createDirectories(file.getParent());
        try (OutputStream out = Files.newOutputStream(file)) { props.store(out, "PlugTrace web token hashes"); }
    }

    private static String hash(String token) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        return java.util.HexFormat.of().formatHex(digest);
    }
}
