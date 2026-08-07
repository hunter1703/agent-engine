package com.agentengine.chaos.api;

import java.util.Locale;

public enum FaultOutcome {
    UNKNOWN,
    INJECTED,
    REMOVED,
    INJECTION_FAILED,
    REMOVAL_FAILED;

    public static FaultOutcome valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return FaultOutcome.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
