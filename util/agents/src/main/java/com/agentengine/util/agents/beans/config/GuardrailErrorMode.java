package com.agentengine.util.agents.beans.config;

import java.util.Locale;

/** Fallback behavior when guardrail evaluation fails unexpectedly. */
public enum GuardrailErrorMode {
    /** Fallback for invalid or missing config values. */
    UNKNOWN,
    /** Allow execution when guardrails fail (higher availability, lower safety). */
    FAIL_OPEN,
    /** Block execution when guardrails fail (higher safety, lower availability). */
    FAIL_CLOSED;

    public static GuardrailErrorMode valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return GuardrailErrorMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
