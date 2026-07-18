package dev.pluglabs.plugtrace.domain;

/** Confidence band for suspects — never claim false precision. */
public enum ConfidenceBand {
    UNKNOWN,
    LOW,
    MEDIUM,
    HIGH
}
