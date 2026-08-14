package com.agentengine.util.agents.beans.config;

import java.util.Locale;

/** Runtime orchestration strategy for orchestrator agents. */
public enum OrchestrationMode {
    /** Fallback for invalid or missing config values. */
    UNKNOWN,
    /** Deterministic sequential execution over sub-agents. */
    SEQUENTIAL,
    /** Deterministic parallel execution over sub-agents. */
    PARALLEL,
    /** LLM-driven manager that spawns, messages, and awaits configurable sub-agents. */
    MANAGER;

    public static OrchestrationMode valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return OrchestrationMode.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return UNKNOWN;
        }
    }
}
