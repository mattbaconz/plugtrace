package dev.pluglabs.plugtrace.paper;

import java.io.InputStream;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Declarative package-prefix registry for frameworks that often appear between caller and failure. */
final class WrapperRegistry {
    private final Map<String, String> prefixes;

    private WrapperRegistry(Map<String, String> prefixes) {
        this.prefixes = Map.copyOf(prefixes);
    }

    static WrapperRegistry load(InputStream input) {
        if (input == null) {
            return new WrapperRegistry(Map.of());
        }
        try (input) {
            Properties properties = new Properties();
            properties.load(input);
            Map<String, String> loaded = new LinkedHashMap<>();
            properties.stringPropertyNames().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .forEach(prefix -> loaded.put(prefix, properties.getProperty(prefix)));
            return new WrapperRegistry(loaded);
        } catch (Exception ignored) {
            return new WrapperRegistry(Map.of());
        }
    }

    static WrapperRegistry packaged() {
        return load(WrapperRegistry.class.getResourceAsStream("/wrapper-registry.properties"));
    }

    Optional<String> ownerFor(String className) {
        if (className == null) return Optional.empty();
        return prefixes.entrySet().stream()
                .filter(entry -> className.startsWith(entry.getKey()))
                .map(Map.Entry::getValue)
                .findFirst();
    }
}
