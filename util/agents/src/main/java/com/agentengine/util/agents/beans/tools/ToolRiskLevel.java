package com.agentengine.util.agents.beans.tools;

import java.util.Locale;

/** Relative risk classification for tools used by tool guardrails and policy rules. */
public enum ToolRiskLevel {
    /** Fallback for invalid or missing config values. */
    UNKNOWN,
    /** Read-only, reversible, or low-impact operations. */
    LOW,
    /** Operations with limited side effects or bounded external calls. */
    MEDIUM,
    /** High-impact operations such as shell commands or external mutations. */
    HIGH,
    /** Irreversible or highly sensitive operations requiring strict controls. */
    CRITICAL;

    public static ToolRiskLevel valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return ToolRiskLevel.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }

    public static boolean atLeast(final ToolRiskLevel actual, final ToolRiskLevel expected) {
        final ToolRiskLevel left = actual == null ? UNKNOWN : actual;
        final ToolRiskLevel right = expected == null ? UNKNOWN : expected;
        return left.ordinal() >= right.ordinal();
    }
}
