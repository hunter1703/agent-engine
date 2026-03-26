package com.agentengine.runtime.actor;

/** Typed classification of a {@link SessionEvent}, derived from its metadata. */
public enum SessionEventKind {
    UNKNOWN,
    PLAIN,
    CORRECTION,
    INTERNAL,
    CHILD_COMPLETED,
    CHILD_FAILED,
    CHILD_PAUSED;

    public boolean isChildEvent() {
        return this == CHILD_COMPLETED
                || this == CHILD_FAILED
                || this == CHILD_PAUSED;
    }
}
