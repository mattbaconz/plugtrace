package dev.pluglabs.plugtrace.domain;

import java.util.List;
import java.util.Objects;

public final class Suspect {
    private final String componentKey;
    private final String changeSummary;
    private final ConfidenceBand band;
    private final int rank;
    private final List<Evidence> supporting;
    private final List<Evidence> contradictions;

    public Suspect(
            String componentKey,
            String changeSummary,
            ConfidenceBand band,
            int rank,
            List<Evidence> supporting,
            List<Evidence> contradictions
    ) {
        this.componentKey = Objects.requireNonNull(componentKey, "componentKey");
        this.changeSummary = changeSummary == null ? "" : changeSummary;
        this.band = Objects.requireNonNull(band, "band");
        this.rank = rank;
        this.supporting = supporting == null ? List.of() : List.copyOf(supporting);
        this.contradictions = contradictions == null ? List.of() : List.copyOf(contradictions);
    }

    public String componentKey() {
        return componentKey;
    }

    public String changeSummary() {
        return changeSummary;
    }

    public ConfidenceBand band() {
        return band;
    }

    public int rank() {
        return rank;
    }

    public List<Evidence> supporting() {
        return supporting;
    }

    public List<Evidence> contradictions() {
        return contradictions;
    }
}
