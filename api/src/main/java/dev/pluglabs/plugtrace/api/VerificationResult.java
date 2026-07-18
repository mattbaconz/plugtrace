package dev.pluglabs.plugtrace.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public record VerificationResult(VerificationStatus status, String summary, Map<String, Object> safeDetails) {
    public VerificationResult {
        Objects.requireNonNull(status, "status");
        summary = summary == null ? "" : summary;
        safeDetails = safeDetails == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(safeDetails));
    }

    public static VerificationResult pass(String summary, Map<String, Object> details) {
        return new VerificationResult(VerificationStatus.PASS, summary, details);
    }
}
