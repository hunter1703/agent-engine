package com.agentengine.util.agents.beans;

import java.util.Locale;

public enum InterruptKind {
    UNKNOWN,
    DECISION,
    TEXT;

    public static InterruptKind valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return InterruptKind.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return UNKNOWN;
        }
    }
}
