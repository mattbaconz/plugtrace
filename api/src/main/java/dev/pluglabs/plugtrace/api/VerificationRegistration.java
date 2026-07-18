package dev.pluglabs.plugtrace.api;

public interface VerificationRegistration extends AutoCloseable {
    VerificationRegistration NOOP = () -> { };

    @Override
    void close();
}
