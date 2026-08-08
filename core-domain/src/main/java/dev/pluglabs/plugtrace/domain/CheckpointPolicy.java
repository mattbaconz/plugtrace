package dev.pluglabs.plugtrace.domain;

import java.util.Objects;

/**
 * Guards checkpoint creation.
 * HEALTHY may checkpoint. UNKNOWN may be promoted to HEALTHY by the caller
 * (operator is locking a seed). FAILING / DEGRADED / CRASHED stay rejected.
 */
public final class CheckpointPolicy {
    private CheckpointPolicy() {
    }

    public enum Decision {
        /** Already HEALTHY — create checkpoint. */
        ALLOW,
        /** UNKNOWN — caller should mark HEALTHY, then create. */
        PROMOTE_UNKNOWN,
        /** Broken / degraded — refuse. */
        REJECT
    }

    public static Decision decide(DeploymentHealth health) {
        Objects.requireNonNull(health, "health");
        return switch (health) {
            case HEALTHY -> Decision.ALLOW;
            case UNKNOWN -> Decision.PROMOTE_UNKNOWN;
            case FAILING, DEGRADED, CRASHED -> Decision.REJECT;
        };
    }

    public static void requireHealthy(Deployment deployment) {
        Objects.requireNonNull(deployment, "deployment");
        if (deployment.health() != DeploymentHealth.HEALTHY) {
            throw new IllegalStateException("A checkpoint requires a HEALTHY deployment; current state is "
                    + deployment.health());
        }
    }

    public static void requireNotBroken(Deployment deployment) {
        Objects.requireNonNull(deployment, "deployment");
        Decision decision = decide(deployment.health());
        if (decision == Decision.REJECT) {
            throw new IllegalStateException("A checkpoint requires a HEALTHY deployment; current state is "
                    + deployment.health()
                    + ". Stabilize (or /plugtrace mark healthy when stable), then retry.");
        }
    }
}
