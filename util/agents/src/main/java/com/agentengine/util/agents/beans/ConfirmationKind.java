package com.agentengine.util.agents.beans;

import java.util.Locale;

public enum ConfirmationKind {
    UNKNOWN,
    DECISION,
    TEXT;

    public static ConfirmationKind valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return ConfirmationKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
