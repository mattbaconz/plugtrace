package dev.pluglabs.plugtrace.api;

import java.time.Duration;
import java.util.Locale;
import java.util.Objects;

public record VerificationCheckDefinition(
        String pluginId,
        String checkId,
        String displayName,
        VerificationCriticality criticality,
        VerificationExecution execution,
        Duration timeout
) {
    public VerificationCheckDefinition {
        Objects.requireNonNull(pluginId, "pluginId");
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(criticality, "criticality");
        Objects.requireNonNull(execution, "execution");
        timeout = timeout == null ? Duration.ofSeconds(10) : timeout;
        if (timeout.isNegative() || timeout.isZero()) throw new IllegalArgumentException("timeout must be positive");
    }

    public String qualifiedId() {
        return pluginId + ":" + checkId.toLowerCase(Locale.ROOT);
    }
}
