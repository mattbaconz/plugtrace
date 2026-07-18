package dev.pluglabs.plugtrace.domain;

import java.util.Objects;

public final class Change {
    private final ChangeType type;
    private final String componentKey;
    private final String before;
    private final String after;
    private final String explanation;
    private final int significance;

    public Change(ChangeType type, String componentKey, String before, String after, String explanation, int significance) {
        this.type = Objects.requireNonNull(type, "type");
        this.componentKey = componentKey == null ? "" : componentKey;
        this.before = before;
        this.after = after;
        this.explanation = explanation == null ? "" : explanation;
        this.significance = significance;
    }

    public ChangeType type() {
        return type;
    }

    public String componentKey() {
        return componentKey;
    }

    public String before() {
        return before;
    }

    public String after() {
        return after;
    }

    public String explanation() {
        return explanation;
    }

    public int significance() {
        return significance;
    }
}
