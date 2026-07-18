package dev.pluglabs.plugtrace.api;

import java.util.concurrent.CompletionStage;

@FunctionalInterface
public interface VerificationCheck {
    CompletionStage<VerificationResult> run(VerificationContext context);
}
