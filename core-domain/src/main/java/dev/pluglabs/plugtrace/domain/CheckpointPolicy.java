package dev.pluglabs.plugtrace.domain;

import java.util.Objects;

/** Guards the invariant that a checkpoint points at an immutable known-healthy deployment. */
public final class CheckpointPolicy {
    private CheckpointPolicy() {
    }

    public static void requireHealthy(Deployment deployment) {
        Objects.requireNonNull(deployment, "deployment");
        if (deployment.health() != DeploymentHealth.HEALTHY) {
            throw new IllegalStateException("A checkpoint requires a HEALTHY deployment; current state is "
                    + deployment.health());
        }
    }
}
