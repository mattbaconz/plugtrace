package dev.pluglabs.plugtrace.domain;

import java.time.Instant;
import java.util.List;

/** Explicit capabilities an operator expects to survive a deployment. */
public record ExpectedState(
        String id,
        String nodeId,
        String sourceDeploymentId,
        Instant capturedAt,
        List<String> plugins,
        List<String> commands,
        List<String> worlds,
        List<String> services
) {
    public ExpectedState {
        plugins = plugins == null ? List.of() : List.copyOf(plugins);
        commands = commands == null ? List.of() : List.copyOf(commands);
        worlds = worlds == null ? List.of() : List.copyOf(worlds);
        services = services == null ? List.of() : List.copyOf(services);
    }
}
