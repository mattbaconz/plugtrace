package dev.pluglabs.plugtrace.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Declarative known-noise fingerprints — no remote fetch, no executable code.
 * <p>
 * Suppression marks matching issues {@code EXPECTED} (quiet). Prefer documenting
 * and annotating known churn ({@code knownChurn*}) over broad suppression so
 * operators still see context.
 */
public final class NoiseRules {
    private final Set<String> suppressedFingerprints;
    private final Set<String> suppressedMessageSubstrings;
    private final Set<String> knownChurnComponents;
    private final Set<String> knownChurnMessageSubstrings;

    public NoiseRules(
            Set<String> suppressedFingerprints,
            Set<String> suppressedMessageSubstrings,
            Set<String> knownChurnComponents,
            Set<String> knownChurnMessageSubstrings
    ) {
        this.suppressedFingerprints = suppressedFingerprints == null
                ? Set.of()
                : Set.copyOf(suppressedFingerprints);
        this.suppressedMessageSubstrings = suppressedMessageSubstrings == null
                ? Set.of()
                : Set.copyOf(suppressedMessageSubstrings);
        this.knownChurnComponents = knownChurnComponents == null
                ? Set.of()
                : Set.copyOf(knownChurnComponents);
        this.knownChurnMessageSubstrings = knownChurnMessageSubstrings == null
                ? Set.of()
                : Set.copyOf(knownChurnMessageSubstrings);
    }

    public static NoiseRules empty() {
        return new NoiseRules(Set.of(), Set.of(), Set.of(), Set.of());
    }

    /** Shipped defaults: PlugDev bootstrap / author-loop churn is context, not silent hide. */
    public static NoiseRules plugDevDefaults() {
        return new NoiseRules(
                Set.of(),
                Set.of(),
                Set.of("PlugDev-Bootstrap", "plugdev-bootstrap", "PLUGIN:plugdev-bootstrap"),
                Set.of("plugdev-bootstrap", "PlugDev-Bootstrap")
        );
    }

    public boolean isSuppressed(Issue issue) {
        if (issue == null) {
            return false;
        }
        if (suppressedFingerprints.contains(issue.fingerprint())) {
            return true;
        }
        String message = issue.normalizedMessage() == null ? "" : issue.normalizedMessage().toLowerCase(Locale.ROOT);
        for (String substring : suppressedMessageSubstrings) {
            if (!substring.isBlank() && message.contains(substring.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean isKnownChurnComponent(String componentKey) {
        if (componentKey == null || componentKey.isBlank()) {
            return false;
        }
        String lower = componentKey.toLowerCase(Locale.ROOT);
        for (String known : knownChurnComponents) {
            if (!known.isBlank() && lower.contains(known.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean isKnownChurnMessage(String message) {
        if (message == null || message.isBlank()) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        for (String substring : knownChurnMessageSubstrings) {
            if (!substring.isBlank() && lower.contains(substring.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    public boolean isKnownChurnChange(Change change) {
        if (change == null) {
            return false;
        }
        return isKnownChurnComponent(change.componentKey())
                || isKnownChurnMessage(change.explanation());
    }

    public boolean isKnownChurnIssue(Issue issue) {
        if (issue == null) {
            return false;
        }
        if (isKnownChurnMessage(issue.normalizedMessage())) {
            return true;
        }
        for (String ownership : issue.ownershipCandidates()) {
            if (isKnownChurnComponent(ownership) || isKnownChurnMessage(ownership)) {
                return true;
            }
        }
        return false;
    }

    public Set<String> suppressedFingerprints() {
        return suppressedFingerprints;
    }

    public Set<String> knownChurnComponents() {
        return knownChurnComponents;
    }

    public static NoiseRules fromLists(
            List<String> fingerprints,
            List<String> messageSubstrings,
            List<String> churnComponents,
            List<String> churnMessageSubstrings
    ) {
        return new NoiseRules(
                fingerprints == null ? Set.of() : new HashSet<>(fingerprints),
                messageSubstrings == null ? Set.of() : new HashSet<>(messageSubstrings),
                churnComponents == null ? Set.of() : new HashSet<>(churnComponents),
                churnMessageSubstrings == null ? Set.of() : new HashSet<>(churnMessageSubstrings)
        );
    }

    /** Backward-compatible loader for tests that only pass suppress lists. */
    public static NoiseRules fromLists(List<String> fingerprints, List<String> messageSubstrings) {
        return fromLists(fingerprints, messageSubstrings, List.of(), List.of());
    }
}
