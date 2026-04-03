package com.agentengine.util.agents.beans;

public enum SessionEventType {
    UNKNOWN,
    CONTENT,
    CONFIRMATION_REQUESTED,
    RUN_FINISHED;

    public static SessionEventType valueOfOrDefault(final String value) {
        if (value == null) {
            return UNKNOWN;
        }
        try {
            return valueOf(value);
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }
}
