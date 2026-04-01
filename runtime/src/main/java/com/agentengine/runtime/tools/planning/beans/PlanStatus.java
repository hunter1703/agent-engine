package com.agentengine.runtime.tools.planning.beans;

import java.util.Locale;

public enum PlanStatus {
    UNKNOWN("unknown"),
    IN_PROGRESS("in_progress"),
    DONE("done", true),
    ABANDONED("abandoned", true);

    private final String value;
    private final boolean terminal;

    PlanStatus(String value) {
        this(value, false);
    }

    PlanStatus(String value, boolean terminal) {
        this.value = value;
        this.terminal = terminal;
    }

    public String getValue() {
        return value;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public static PlanStatus valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return PlanStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
