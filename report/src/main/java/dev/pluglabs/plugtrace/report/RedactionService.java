package dev.pluglabs.plugtrace.report;

import java.util.regex.Pattern;

/** Central redaction helpers — exporters must not invent independent privacy logic. */
public final class RedactionService {
    private static final Pattern SECRET = Pattern.compile(
            "(?i)(password|passwd|token|api[_-]?key|secret|dsn|webhook|license[_-]?key|private[_-]?key)\\s*[:=]\\s*\\S+"
    );
    private static final Pattern BEARER = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9._\\-]+");
    private static final Pattern EMAIL = Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern UUID = Pattern.compile(
            "(?i)\\b[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\b");
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern WINDOWS_PATH = Pattern.compile("(?i)[A-Z]:\\\\(?:[^\\s\\\\]+\\\\?)+");
    private static final Pattern HOME_PATH = Pattern.compile("/(?:home|Users)/[^\\s]+", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANSI_ESCAPE = Pattern.compile("\\u001B\\[[0-?]*[ -/]*[@-~]");
    private static final Pattern CONTROL = Pattern.compile("[\\p{Cc}&&[^\\r\\n\\t]]");

    public String redact(String input) {
        if (input == null || input.isBlank()) {
            return input;
        }
        String value = SECRET.matcher(input).replaceAll("$1=<redacted>");
        value = BEARER.matcher(value).replaceAll("Bearer <redacted>");
        value = EMAIL.matcher(value).replaceAll("<email>");
        value = UUID.matcher(value).replaceAll("<uuid>");
        value = IPV4.matcher(value).replaceAll("<ip>");
        value = WINDOWS_PATH.matcher(value).replaceAll("<path>");
        value = HOME_PATH.matcher(value).replaceAll("<path>");
        value = ANSI_ESCAPE.matcher(value).replaceAll("");
        value = CONTROL.matcher(value).replaceAll("");
        return value;
    }
}
