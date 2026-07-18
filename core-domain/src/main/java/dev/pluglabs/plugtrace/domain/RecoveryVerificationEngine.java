package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Deterministic before/after recovery evidence; it never upgrades correlation to causation by itself. */
public final class RecoveryVerificationEngine {
    public RecoveryVerification evaluate(
            String id,
            String deploymentId,
            String restorePlanId,
            Instant verifiedAt,
            double beforeRate,
            double afterRate,
            DeploymentVerification before,
            DeploymentVerification after
    ) {
        Map<String, CheckStatus> beforeChecks = statuses(before);
        Map<String, CheckStatus> afterChecks = statuses(after);
        List<String> deltas = checkDeltas(beforeChecks, afterChecks);
        long beforeFailures = failureCount(beforeChecks);
        long afterFailures = failureCount(afterChecks);
        boolean hadFailureEvidence = beforeRate > 0.0 || beforeFailures > 0;

        RecoveryOutcome outcome;
        if (!hadFailureEvidence) {
            outcome = RecoveryOutcome.INSUFFICIENT_EVIDENCE;
        } else if (afterRate == 0.0 && afterFailures == 0) {
            outcome = RecoveryOutcome.RESOLVED;
        } else if (afterRate < beforeRate || afterFailures < beforeFailures) {
            outcome = RecoveryOutcome.IMPROVED;
        } else if (afterRate > beforeRate || afterFailures > beforeFailures) {
            outcome = RecoveryOutcome.WORSENED;
        } else {
            outcome = RecoveryOutcome.UNCHANGED;
        }

        String summary = "Recovery " + outcome.name().toLowerCase(Locale.ROOT)
                + "; issue rate/min " + String.format(Locale.ROOT, "%.2f -> %.2f", beforeRate, afterRate)
                + "; changed checks=" + deltas.size();
        return new RecoveryVerification(id, deploymentId, restorePlanId, verifiedAt, outcome,
                beforeRate, afterRate, deltas, summary);
    }

    private static Map<String, CheckStatus> statuses(DeploymentVerification verification) {
        if (verification == null) return Map.of();
        Map<String, CheckStatus> result = new LinkedHashMap<>();
        for (CheckResult check : verification.checks()) {
            result.put(check.checkId(), check.status());
        }
        return result;
    }

    private static List<String> checkDeltas(
            Map<String, CheckStatus> before, Map<String, CheckStatus> after) {
        LinkedHashSet<String> ids = new LinkedHashSet<>(before.keySet());
        ids.addAll(after.keySet());
        List<String> deltas = new ArrayList<>();
        ids.stream().sorted().forEach(id -> {
            CheckStatus from = before.getOrDefault(id, CheckStatus.UNKNOWN);
            CheckStatus to = after.getOrDefault(id, CheckStatus.UNKNOWN);
            if (from != to) deltas.add(id + ": " + from + " -> " + to);
        });
        return List.copyOf(deltas);
    }

    private static long failureCount(Map<String, CheckStatus> checks) {
        return checks.values().stream()
                .filter(status -> status == CheckStatus.FAIL || status == CheckStatus.WARN)
                .count();
    }
}
