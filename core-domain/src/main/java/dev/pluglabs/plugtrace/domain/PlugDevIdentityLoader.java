package dev.pluglabs.plugtrace.domain;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Loads PlugDev identity JSON from conventional locations.
 * Search order: PlugTrace data folder, server root, .plugdev/.
 */
public final class PlugDevIdentityLoader {
    private static final Pattern STRING_FIELD = Pattern.compile(
            "\"(schemaVersion|gitCommit|buildSystem|buildTask|artifactHash|projectName|sessionId|plugdevVersion|recordedAt)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\""
    );
    private static final Pattern BOOL_FIELD = Pattern.compile("\"gitDirty\"\\s*:\\s*(true|false)");

    private PlugDevIdentityLoader() {
    }

    public static Optional<PlugDevIdentity> load(Path dataFolder, Path serverRoot) {
        Path[] candidates = new Path[] {
                dataFolder == null ? null : dataFolder.resolve("plugdev-identity.json"),
                serverRoot == null ? null : serverRoot.resolve("plugdev-identity.json"),
                serverRoot == null ? null : serverRoot.resolve(".plugdev").resolve("plugtrace-identity.json")
        };
        for (Path candidate : candidates) {
            Optional<PlugDevIdentity> loaded = tryLoad(candidate);
            if (loaded.isPresent()) {
                return loaded;
            }
        }
        return Optional.empty();
    }

    public static Optional<PlugDevIdentity> tryLoad(Path path) {
        if (path == null || !Files.isRegularFile(path)) {
            return Optional.empty();
        }
        try {
            String json = Files.readString(path);
            Map<String, Object> map = parseLooseObject(json);
            return Optional.ofNullable(PlugDevIdentity.fromMap(map));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    static Map<String, Object> parseLooseObject(String json) {
        Map<String, Object> map = new LinkedHashMap<>();
        Matcher strings = STRING_FIELD.matcher(json);
        while (strings.find()) {
            map.put(strings.group(1), unescape(strings.group(2)));
        }
        Matcher dirty = BOOL_FIELD.matcher(json);
        if (dirty.find()) {
            map.put("gitDirty", Boolean.parseBoolean(dirty.group(1)));
        }
        return map;
    }

    private static String unescape(String value) {
        return value
                .replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\t", "\t");
    }
}
