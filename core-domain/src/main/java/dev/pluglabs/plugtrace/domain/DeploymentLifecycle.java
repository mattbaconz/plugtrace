package dev.pluglabs.plugtrace.domain;

public enum DeploymentLifecycle {
    DETECTED,
    SNAPSHOTTING,
    STARTING,
    OBSERVING,
    STOPPED_CLEANLY,
    CRASHED,
    KILLED,
    INCOMPLETE
}
