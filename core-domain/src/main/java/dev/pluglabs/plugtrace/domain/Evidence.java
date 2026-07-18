package dev.pluglabs.plugtrace.domain;

import java.util.List;
import java.util.Objects;

public final class Evidence {
    private final String source;
    private final String direction;
    private final String explanation;
    private final int strength;

    public Evidence(String source, String direction, String explanation, int strength) {
        this.source = Objects.requireNonNull(source, "source");
        this.direction = direction == null ? "supporting" : direction;
        this.explanation = explanation == null ? "" : explanation;
        this.strength = strength;
    }

    public String source() {
        return source;
    }

    public String direction() {
        return direction;
    }

    public String explanation() {
        return explanation;
    }

    public int strength() {
        return strength;
    }

    public boolean supporting() {
        return !"contradicting".equalsIgnoreCase(direction);
    }
}
