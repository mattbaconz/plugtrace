package dev.pluglabs.plugtrace.domain;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Declarative known-noise fingerprints — no remote fetch, no executable code. */
public final class NoiseRules {
    private final Set<String> suppressedFingerprints;
    private final Set<String> suppressedMessageSubstrings;

    public NoiseRules(Set<String> suppressedFingerprints, Set<String> suppressedMessageSubstrings) {
        this.suppressedFingerprints = suppressedFingerprints == null
                ? Set.of()
                : Set.copyOf(suppressedFingerprints);
        this.suppressedMessageSubstrings = suppressedMessageSubstrings == null
                ? Set.of()
                : Set.copyOf(suppressedMessageSubstrings);
    }

    public static NoiseRules empty() {
        return new NoiseRules(Set.of(), Set.of());
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

    public Set<String> suppressedFingerprints() {
        return suppressedFingerprints;
    }

    public static NoiseRules fromLists(List<String> fingerprints, List<String> messageSubstrings) {
        return new NoiseRules(
                fingerprints == null ? Set.of() : new HashSet<>(fingerprints),
                messageSubstrings == null ? Set.of() : new HashSet<>(messageSubstrings)
        );
    }
}
