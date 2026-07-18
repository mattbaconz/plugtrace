package dev.pluglabs.plugtrace.domain;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Attribution v0: ownership + change proximity. Never blame solely because something changed last.
 */
public final class AttributionEngine {
    public List<Suspect> attribute(List<Issue> issues, List<Change> changes) {
        if (issues == null || issues.isEmpty()) {
            return List.of(new Suspect(
                    "unknown",
                    "No issues observed.",
                    ConfidenceBand.UNKNOWN,
                    1,
                    List.of(new Evidence("observation", "supporting", "No new issues to attribute.", 1)),
                    List.of()
            ));
        }

        Map<String, Candidate> candidates = new LinkedHashMap<>();

        for (Change change : changes) {
            if (change.componentKey() == null || change.componentKey().isBlank()) {
                continue;
            }
            if (change.type() == ChangeType.COMPONENT_ADDED
                    || change.type() == ChangeType.COMPONENT_REMOVED
                    || change.type() == ChangeType.VERSION_CHANGED
                    || change.type() == ChangeType.BINARY_CHANGED_SAME_VERSION
                    || change.type() == ChangeType.LOAD_OUTCOME_CHANGED
                    || change.type() == ChangeType.RUNTIME_CHANGED
                    || change.type() == ChangeType.CONFIG_HASH_CHANGED) {
                Candidate candidate = candidates.computeIfAbsent(normalizeKey(change.componentKey()), Candidate::new);
                candidate.supporting.add(new Evidence(
                        "change",
                        "supporting",
                        change.explanation(),
                        change.significance()
                ));
                candidate.changeSummary = change.explanation();
                candidate.score += change.significance();
            }
        }

        for (Issue issue : issues) {
            if (issue.status() != IssueStatus.NEW
                    && issue.regressionClass() != RegressionClass.NEW_ISSUE
                    && issue.regressionClass() != RegressionClass.STARTUP_REGRESSION) {
                continue;
            }
            for (String owner : issue.ownershipCandidates()) {
                OwnershipHint hint = OwnershipHint.parse(owner);
                if (hint.kind == OwnershipKind.WRAPPER) {
                    continue;
                }
                String key = normalizeKey(hint.owner);
                Candidate candidate = candidates.computeIfAbsent(key, Candidate::new);
                int strength = hint.kind.strong ? 80 : 35;
                String explanation = switch (hint.kind) {
                    case FRAME -> "First meaningful stack frame is owned by " + hint.owner;
                    case LIFECYCLE -> "Plugin lifecycle evidence is owned by " + hint.owner;
                    case LOGGER, LEGACY -> "Logger ownership hint includes " + hint.owner;
                    case WRAPPER -> throw new IllegalStateException("wrapper handled above");
                };
                candidate.supporting.add(new Evidence(
                        "ownership",
                        "supporting",
                        explanation,
                        strength
                ));
                candidate.score += strength;
                candidate.strongOwnership |= hint.kind.strong;
            }

            // Contradiction: if issue ownership does not match any changed component, note it.
            boolean ownershipMatchedChange = issue.ownershipCandidates().stream().anyMatch(owner -> {
                OwnershipHint hint = OwnershipHint.parse(owner);
                if (hint.kind == OwnershipKind.WRAPPER) return false;
                String key = normalizeKey(hint.owner);
                return changes.stream().anyMatch(c -> normalizeKey(c.componentKey()).equals(key));
            });
            boolean hasNonWrapperOwnership = issue.ownershipCandidates().stream()
                    .map(OwnershipHint::parse).anyMatch(hint -> hint.kind != OwnershipKind.WRAPPER);
            if (!ownershipMatchedChange && hasNonWrapperOwnership) {
                for (Change change : changes) {
                    Candidate candidate = candidates.get(normalizeKey(change.componentKey()));
                    if (candidate != null) {
                        candidate.contradictions.add(new Evidence(
                                "ownership",
                                "contradicting",
                                "Top ownership hints do not match this changed component.",
                                40
                        ));
                        candidate.score -= 15;
                    }
                }
            }
        }

        if (candidates.isEmpty()) {
            return List.of(new Suspect(
                    "unknown",
                    "Insufficient evidence to name a suspect.",
                    ConfidenceBand.UNKNOWN,
                    1,
                    List.of(new Evidence(
                            "attribution",
                            "supporting",
                            "No ownership or change proximity evidence was strong enough.",
                            1
                    )),
                    List.of()
            ));
        }

        List<Suspect> suspects = new ArrayList<>();
        List<Candidate> ranked = candidates.values().stream()
                .sorted(Comparator.comparingInt((Candidate c) -> c.score).reversed())
                .toList();

        int rank = 1;
        for (Candidate candidate : ranked) {
            ConfidenceBand band = bandFor(candidate);
            suspects.add(new Suspect(
                    candidate.key,
                    candidate.changeSummary == null ? candidate.key : candidate.changeSummary,
                    band,
                    rank++,
                    candidate.supporting,
                    candidate.contradictions
            ));
            if (rank > 5) {
                break;
            }
        }
        return suspects;
    }

    private static String normalizeKey(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        if (raw.contains(":")) {
            String[] parts = raw.split(":", 2);
            return parts[0].toUpperCase(Locale.ROOT) + ":" + parts[1].toLowerCase(Locale.ROOT);
        }
        return "PLUGIN:" + raw.toLowerCase(Locale.ROOT);
    }

    private static ConfidenceBand bandFor(Candidate candidate) {
        boolean hasOwnership = candidate.supporting.stream().anyMatch(e -> "ownership".equals(e.source()));
        boolean hasChange = candidate.supporting.stream().anyMatch(e -> "change".equals(e.source()));
        boolean hasContradiction = !candidate.contradictions.isEmpty();

        if (candidate.strongOwnership && hasChange && !hasContradiction && candidate.score >= 120) {
            return ConfidenceBand.HIGH;
        }
        if ((hasOwnership && hasChange) || candidate.score >= 90) {
            return hasContradiction ? ConfidenceBand.MEDIUM : ConfidenceBand.MEDIUM;
        }
        if (hasOwnership || hasChange) {
            return ConfidenceBand.LOW;
        }
        return ConfidenceBand.UNKNOWN;
    }

    private static final class Candidate {
        private final String key;
        private String changeSummary;
        private int score;
        private boolean strongOwnership;
        private final List<Evidence> supporting = new ArrayList<>();
        private final List<Evidence> contradictions = new ArrayList<>();

        private Candidate(String key) {
            this.key = normalizeKey(key);
        }
    }

    private enum OwnershipKind {
        FRAME(true), LIFECYCLE(true), LOGGER(false), WRAPPER(false), LEGACY(false);

        private final boolean strong;

        OwnershipKind(boolean strong) {
            this.strong = strong;
        }
    }

    private record OwnershipHint(OwnershipKind kind, String owner) {
        private static OwnershipHint parse(String raw) {
            if (raw == null || raw.isBlank()) return new OwnershipHint(OwnershipKind.LEGACY, "unknown");
            int separator = raw.indexOf(':');
            if (separator <= 0 || separator == raw.length() - 1) {
                return new OwnershipHint(OwnershipKind.LEGACY, raw);
            }
            String prefix = raw.substring(0, separator).toLowerCase(Locale.ROOT);
            String owner = raw.substring(separator + 1);
            return switch (prefix) {
                case "frame" -> new OwnershipHint(OwnershipKind.FRAME, owner);
                case "lifecycle" -> new OwnershipHint(OwnershipKind.LIFECYCLE, owner);
                case "logger" -> new OwnershipHint(OwnershipKind.LOGGER, owner);
                case "wrapper" -> new OwnershipHint(OwnershipKind.WRAPPER, owner);
                default -> new OwnershipHint(OwnershipKind.LEGACY, raw);
            };
        }
    }
}
