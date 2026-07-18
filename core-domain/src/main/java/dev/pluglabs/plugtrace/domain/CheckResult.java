package dev.pluglabs.plugtrace.domain;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record CheckResult(
        String checkId,
        String displayName,
        CheckStatus status,
        CheckCriticality criticality,
        String summary,
        Map<String, Object> safeDetails
) {
    public CheckResult {
        Objects.requireNonNull(checkId, "checkId");
        Objects.requireNonNull(displayName, "displayName");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(criticality, "criticality");
        summary = summary == null ? "" : summary;
        safeDetails = safeDetails == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(safeDetails));
    }

    public static CheckResult pass(String id, String name) {
        return new CheckResult(id, name, CheckStatus.PASS, CheckCriticality.CRITICAL, "Passed", Map.of());
    }
}
