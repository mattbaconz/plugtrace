package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Normalized event envelope from platform adapters. */
public final class IssueEvent {
    private final String id;
    private final Instant eventAt;
    private final String deploymentId;
    private final String source;
    private final String severity;
    private final String throwableType;
    private final String message;
    private final String stackTrace;
    private final List<String> ownershipHints;
    private final String threadName;

    public IssueEvent(
            String id,
            Instant eventAt,
            String deploymentId,
            String source,
            String severity,
            String throwableType,
            String message,
            String stackTrace,
            List<String> ownershipHints,
            String threadName
    ) {
        this.id = id == null ? UUID.randomUUID().toString() : id;
        this.eventAt = Objects.requireNonNull(eventAt, "eventAt");
        this.deploymentId = Objects.requireNonNull(deploymentId, "deploymentId");
        this.source = source == null ? "logger" : source;
        this.severity = severity == null ? "error" : severity;
        this.throwableType = throwableType == null ? "java.lang.Throwable" : throwableType;
        this.message = message == null ? "" : message;
        this.stackTrace = stackTrace == null ? "" : stackTrace;
        this.ownershipHints = ownershipHints == null ? List.of() : List.copyOf(ownershipHints);
        this.threadName = threadName;
    }

    public String id() {
        return id;
    }

    public Instant eventAt() {
        return eventAt;
    }

    public String deploymentId() {
        return deploymentId;
    }

    public String source() {
        return source;
    }

    public String severity() {
        return severity;
    }

    public String throwableType() {
        return throwableType;
    }

    public String message() {
        return message;
    }

    public String stackTrace() {
        return stackTrace;
    }

    public List<String> ownershipHints() {
        return ownershipHints;
    }

    public String threadName() {
        return threadName;
    }
}
