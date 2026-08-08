package dev.pluglabs.plugtrace.paper;

/**
 * Bounded throwable formatting for the async ingest worker (not the logging thread).
 */
final class StackTraceFormatter {
    static final int DEFAULT_MAX_FRAMES = 40;

    private StackTraceFormatter() {
    }

    static String format(Throwable thrown) {
        return format(thrown, DEFAULT_MAX_FRAMES);
    }

    static String format(Throwable thrown, int maxFrames) {
        if (thrown == null) {
            return "";
        }
        int budget = Math.max(1, maxFrames);
        StringBuilder sb = new StringBuilder(512);
        Throwable current = thrown;
        int depth = 0;
        while (current != null && depth < 8 && budget > 0) {
            if (depth > 0) {
                sb.append("Caused by: ");
            }
            sb.append(current.getClass().getName());
            String message = current.getMessage();
            if (message != null && !message.isBlank()) {
                sb.append(": ").append(message);
            }
            sb.append('\n');
            StackTraceElement[] frames = current.getStackTrace();
            // Reserve a little budget for deeper causes when present.
            int perThrowable = current.getCause() == null
                    ? budget
                    : Math.max(2, Math.min(budget, Math.max(2, budget / 2)));
            int written = 0;
            for (StackTraceElement frame : frames) {
                if (written >= perThrowable || budget <= 0) {
                    break;
                }
                sb.append("\tat ").append(frame).append('\n');
                budget--;
                written++;
            }
            if (frames.length > written) {
                sb.append("\t…\n");
            }
            current = current.getCause();
            depth++;
        }
        return sb.toString().trim();
    }

    /** Cheap top frames when the full-stack budget is exhausted (still fingerprints NEW issues). */
    static String cheapTop(Throwable thrown, int maxFrames) {
        if (thrown == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(256);
        sb.append(thrown.getClass().getName());
        String message = thrown.getMessage();
        if (message != null && !message.isBlank()) {
            sb.append(": ").append(message);
        }
        sb.append('\n');
        StackTraceElement[] frames = thrown.getStackTrace();
        int keep = Math.min(frames.length, Math.max(1, maxFrames));
        for (int i = 0; i < keep; i++) {
            sb.append("\tat ").append(frames[i]).append('\n');
        }
        if (frames.length > keep) {
            sb.append("\t…\n");
        }
        return sb.toString().trim();
    }

    static String truncate(String stack, int maxLines) {
        if (stack == null || stack.isBlank()) {
            return "";
        }
        String[] lines = stack.split("\\R");
        int keep = Math.min(lines.length, Math.max(1, maxLines));
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < keep; i++) {
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(lines[i]);
        }
        if (lines.length > keep) {
            sb.append("\n…");
        }
        return sb.toString();
    }
}
