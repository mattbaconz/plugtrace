package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class Issue {
    private final String id;
    private final String fingerprint;
    private final String normalizedType;
    private final String normalizedMessage;
    private final List<String> ownershipCandidates;
    private final Instant firstSeenAt;
    private final Instant lastSeenAt;
    private final IssueStatus status;
    private final String severity;
    private final long occurrenceCount;
    private final String sampleStack;
    private final RegressionClass regressionClass;

    public Issue(
            String id,
            String fingerprint,
            String normalizedType,
            String normalizedMessage,
            List<String> ownershipCandidates,
            Instant firstSeenAt,
            Instant lastSeenAt,
            IssueStatus status,
            String severity,
            long occurrenceCount,
            String sampleStack,
            RegressionClass regressionClass
    ) {
        this.id = Objects.requireNonNull(id, "id");
        this.fingerprint = Objects.requireNonNull(fingerprint, "fingerprint");
        this.normalizedType = normalizedType == null ? "exception" : normalizedType;
        this.normalizedMessage = normalizedMessage == null ? "" : normalizedMessage;
        this.ownershipCandidates = ownershipCandidates == null ? List.of() : List.copyOf(ownershipCandidates);
        this.firstSeenAt = Objects.requireNonNull(firstSeenAt, "firstSeenAt");
        this.lastSeenAt = Objects.requireNonNull(lastSeenAt, "lastSeenAt");
        this.status = Objects.requireNonNull(status, "status");
        this.severity = severity == null ? "error" : severity;
        this.occurrenceCount = occurrenceCount;
        this.sampleStack = sampleStack;
        this.regressionClass = regressionClass == null ? RegressionClass.NONE : regressionClass;
    }

    public String id() {
        return id;
    }

    public String fingerprint() {
        return fingerprint;
    }

    public String normalizedType() {
        return normalizedType;
    }

    public String normalizedMessage() {
        return normalizedMessage;
    }

    public List<String> ownershipCandidates() {
        return ownershipCandidates;
    }

    public Instant firstSeenAt() {
        return firstSeenAt;
    }

    public Instant lastSeenAt() {
        return lastSeenAt;
    }

    public IssueStatus status() {
        return status;
    }

    public String severity() {
        return severity;
    }

    public long occurrenceCount() {
        return occurrenceCount;
    }

    public String sampleStack() {
        return sampleStack;
    }

    public RegressionClass regressionClass() {
        return regressionClass;
    }

    public Issue withCount(long count, Instant lastSeen) {
        return new Issue(
                id, fingerprint, normalizedType, normalizedMessage, ownershipCandidates,
                firstSeenAt, lastSeen, status, severity, count, sampleStack, regressionClass
        );
    }

    public Issue withStatus(IssueStatus newStatus, RegressionClass classification) {
        return new Issue(
                id, fingerprint, normalizedType, normalizedMessage, ownershipCandidates,
                firstSeenAt, lastSeenAt, newStatus, severity, occurrenceCount, sampleStack, classification
        );
    }
}
