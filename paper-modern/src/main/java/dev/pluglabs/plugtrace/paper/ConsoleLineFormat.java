package dev.pluglabs.plugtrace.paper;

import java.util.ArrayList;
import java.util.List;

/**
 * Path-aware console line shaping — keeps PlugTrace rows from soft-wrapping mid-token
 * without a brand prefix on the continuation line.
 */
final class ConsoleLineFormat {
    static final int MAX_BODY_COLS = 88;
    static final int MAX_COMPONENT_COLS = 48;
    static final int MAX_EXPLANATION_COLS = 72;

    private ConsoleLineFormat() {
    }

    /** Middle-ellipsis for long paths / keys. */
    static String compactLabel(String text, int maxChars) {
        if (text == null || text.isEmpty() || maxChars < 4 || text.length() <= maxChars) {
            return text == null ? "" : text;
        }
        int keep = maxChars - 1; // …
        int head = keep / 2;
        int tail = keep - head;
        return text.substring(0, head) + "…" + text.substring(text.length() - tail);
    }

    static String shortComponentKey(String key) {
        if (key == null) {
            return "";
        }
        String k = key;
        if (k.regionMatches(true, 0, "PLUGIN:", 0, 7)) {
            k = k.substring(7);
        }
        return compactLabel(k, MAX_COMPONENT_COLS);
    }

    /** Drop duplicated config paths already present in the component key. */
    static String shortenExplanation(String explanation, String component) {
        if (explanation == null || explanation.isBlank()) {
            return "";
        }
        String trimmed = explanation.trim();
        String prefix;
        if (trimmed.startsWith("Config appeared: ")) {
            prefix = "Config appeared";
        } else if (trimmed.startsWith("Config hash changed: ")) {
            prefix = "Config hash changed";
        } else {
            return trimmed;
        }
        String path = trimmed.substring(trimmed.indexOf(':') + 1).trim();
        if (path.isEmpty()) {
            return prefix;
        }
        if (component != null && (component.contains(path) || component.endsWith(path))) {
            return prefix;
        }
        return prefix + ": " + compactLabel(path, 40);
    }

    /** Word/path-aware wrap; each chunk is meant as its own prefixed message. */
    static List<String> wrapPlain(String text, int maxCols) {
        if (text == null || text.isEmpty()) {
            return List.of("");
        }
        if (maxCols < 8 || text.length() <= maxCols) {
            return List.of(text);
        }
        List<String> out = new ArrayList<>();
        int i = 0;
        int n = text.length();
        while (i < n) {
            int end = Math.min(n, i + maxCols);
            if (end < n) {
                int breakAt = -1;
                int minBreak = i + (maxCols / 2);
                for (int j = end; j > minBreak; j--) {
                    char c = text.charAt(j - 1);
                    if (c == ' ' || c == '/' || c == ':' || c == '-' || c == '_' || c == '.') {
                        breakAt = j;
                        break;
                    }
                }
                if (breakAt > i) {
                    end = breakAt;
                }
            }
            String chunk = text.substring(i, end).stripTrailing();
            if (!chunk.isEmpty()) {
                out.add(chunk);
            }
            i = end;
            while (i < n && text.charAt(i) == ' ') {
                i++;
            }
        }
        return out.isEmpty() ? List.of(text) : out;
    }
}
