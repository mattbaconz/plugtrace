package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;

/** Before/after evidence for an explicitly applied recovery plan. */
public record RecoveryVerification(
        String id,
        String deploymentId,
        String restorePlanId,
        Instant verifiedAt,
        RecoveryOutcome outcome,
        double beforeIssueRate,
        double afterIssueRate,
        List<String> changedChecks,
        String summary
) {
    public RecoveryVerification {
        changedChecks = changedChecks == null ? List.of() : List.copyOf(changedChecks);
        summary = summary == null ? "" : summary;
    }
}
