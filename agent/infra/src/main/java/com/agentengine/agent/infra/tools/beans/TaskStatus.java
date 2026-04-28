package com.agentengine.agent.infra.tools.beans;

import java.util.Locale;

public enum TaskStatus {
    UNKNOWN("unknown"),
    TODO("todo"),
    IN_PROGRESS("in_progress"),
    DONE("done", true),
    ABANDONED("abandoned", true);

    private final String value;
    private final boolean terminal;

    TaskStatus(String value) {
        this(value, false);
    }

    TaskStatus(String value, boolean terminal) {
        this.value = value;
        this.terminal = terminal;
    }

    public String getValue() {
        return value;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public static TaskStatus valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return TaskStatus.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
