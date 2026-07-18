package dev.pluglabs.plugtrace.domain;

import java.util.Locale;
import java.util.Comparator;
import java.util.stream.Collectors;
import java.util.regex.Pattern;

/**
 * Normalizes dynamic identifiers so equivalent errors group together.
 * Over-normalization merges distinct bugs; under-normalization fragments one bug.
 */
public final class FingerprintEngine {
    private static final Pattern UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}"
    );
    private static final Pattern ISO_TIME = Pattern.compile(
            "\\d{4}-\\d{2}-\\d{2}[T ]\\d{2}:\\d{2}:\\d{2}(?:\\.\\d+)?(?:Z|[+-]\\d{2}:?\\d{2})?"
    );
    private static final Pattern IPV4 = Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b");
    private static final Pattern NUMBER = Pattern.compile("\\b\\d{4,}\\b");
    private static final Pattern PATH_SEGMENT = Pattern.compile("[A-Za-z]:\\\\[^\\s]+|/home/[^\\s]+|/tmp/[^\\s]+");
    private static final Pattern LINE_NUMBER = Pattern.compile(":\\d+(?=\\)|$)");

    public String fingerprint(IssueEvent event) {
        String type = normalize(event.throwableType());
        String message = normalize(event.message());
        String topFrames = topMeaningfulFrames(event.stackTrace(), 5);
        String rootCauseChain = rootCauseChain(event.stackTrace());
        String ownership = event.ownershipHints().stream()
                .map(this::normalize)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.joining(";"));
        String context = normalize(event.source()) + ":" + normalizeThreadContext(event.threadName());
        return Sha256Hasher.hashString(type + "|" + message + "|" + rootCauseChain + "|"
                + topFrames + "|" + ownership + "|" + context);
    }

    public String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String value = input;
        value = UUID.matcher(value).replaceAll("<uuid>");
        value = ISO_TIME.matcher(value).replaceAll("<time>");
        value = IPV4.matcher(value).replaceAll("<ip>");
        value = PATH_SEGMENT.matcher(value).replaceAll("<path>");
        value = LINE_NUMBER.matcher(value).replaceAll(":<n>");
        value = NUMBER.matcher(value).replaceAll("<n>");
        value = value.replaceAll("\\s+", " ").trim().toLowerCase(Locale.ROOT);
        return value;
    }

    public String topMeaningfulFrames(String stackTrace, int limit) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        int count = 0;
        for (String line : stackTrace.split("\\R")) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("at ")) {
                continue;
            }
            String frame = trimmed.substring(3);
            if (frame.startsWith("java.")
                    || frame.startsWith("jdk.")
                    || frame.startsWith("sun.")
                    || frame.startsWith("com.sun.")
                    || frame.startsWith("net.minecraft.server.")
                    || frame.startsWith("org.bukkit.")
                    || frame.startsWith("io.papermc.")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(';');
            }
            builder.append(normalize(LINE_NUMBER.matcher(frame).replaceAll(":<n>")));
            count++;
            if (count >= limit) {
                break;
            }
        }
        return builder.toString();
    }

    public String rootCauseChain(String stackTrace) {
        if (stackTrace == null || stackTrace.isBlank()) {
            return "";
        }
        return java.util.Arrays.stream(stackTrace.split("\\R"))
                .map(String::trim)
                .filter(line -> line.startsWith("Caused by:"))
                .map(line -> normalize(line.substring("Caused by:".length())))
                .collect(Collectors.joining(";"));
    }

    private String normalizeThreadContext(String threadName) {
        String normalized = normalize(threadName);
        if (normalized.contains("region")) return "region";
        if (normalized.contains("async")) return "async";
        if (normalized.contains("server thread") || normalized.contains("global")) return "global";
        return normalized;
    }
}
