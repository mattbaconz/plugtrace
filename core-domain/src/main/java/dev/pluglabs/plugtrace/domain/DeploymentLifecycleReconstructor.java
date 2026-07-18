package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/** Converts a deployment left non-terminal by the previous process into conservative recovered evidence. */
public final class DeploymentLifecycleReconstructor {
    private DeploymentLifecycleReconstructor() {}

    public static Deployment reconstruct(Deployment previous, Instant detectedAt, List<String> crashReports) {
        Objects.requireNonNull(previous, "previous");
        Objects.requireNonNull(detectedAt, "detectedAt");
        List<String> reports = crashReports == null ? List.of() : List.copyOf(crashReports);
        if (previous.endedAt() != null || previous.lifecycle() == DeploymentLifecycle.STOPPED_CLEANLY) {
            return previous;
        }
        DeploymentLifecycle terminal = reports.isEmpty()
                ? DeploymentLifecycle.INCOMPLETE : DeploymentLifecycle.CRASHED;
        return previous.withTermination(detectedAt, terminal, reports);
    }
}
