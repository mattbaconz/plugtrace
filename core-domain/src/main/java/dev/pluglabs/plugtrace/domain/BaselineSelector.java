package dev.pluglabs.plugtrace.domain;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Selects the newest reliable healthy deployment, or none. */
public final class BaselineSelector {
    public Optional<Deployment> select(List<Deployment> history, Deployment current) {
        if (history == null || history.isEmpty()) {
            return Optional.empty();
        }

        List<Deployment> prior = history.stream()
                .filter(d -> d.localSequence() < current.localSequence())
                .sorted(Comparator.comparingLong(Deployment::localSequence).reversed())
                .toList();

        return prior.stream()
                .filter(d -> d.health() == DeploymentHealth.HEALTHY || d.tags().contains("healthy"))
                .findFirst();
    }

    public String describe(Optional<Deployment> baseline) {
        if (baseline.isEmpty()) {
            return "No reliable baseline exists yet.";
        }
        Deployment d = baseline.get();
        return "Deployment #" + d.localSequence()
                + " (" + d.health().name().toLowerCase()
                + ", id=" + d.id().substring(0, Math.min(8, d.id().length())) + ")";
    }
}
