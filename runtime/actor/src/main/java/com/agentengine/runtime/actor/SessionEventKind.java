package com.agentengine.runtime.actor;

import com.agentengine.util.agents.beans.SessionEvent;

/** Typed classification of a {@link SessionEvent}, derived from its metadata. */
public enum SessionEventKind {
    UNKNOWN,
    PLAIN,
    CORRECTION,
    INTERNAL,
    CHILD_COMPLETED,
    CHILD_FAILED,
    CHILD_PAUSED;

    public static SessionEventKind valueOfOrDefault(final String value) {
        if (value == null) return UNKNOWN;
        try {
            return valueOf(value);
        } catch (final IllegalArgumentException e) {
            return UNKNOWN;
        }
    }

    public boolean isChildEvent() {
        return this == CHILD_COMPLETED
                || this == CHILD_FAILED
                || this == CHILD_PAUSED;
    }
}
