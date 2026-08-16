package com.agentengine.scheduler.api.models;

public enum TriggerStatus {
    UNKNOWN,
    /** Created or rescheduled, waiting for its fire time. Not yet picked up. */
    WAITING,
    /** Claimed by the scheduler and handed to a runner; not started yet. */
    QUEUED,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELLED;

    public static TriggerStatus valueOfOrDefault(final String value) {
        if (value == null || value.isBlank()) {
            return UNKNOWN;
        }
        try {
            return TriggerStatus.valueOf(value);
        } catch (final IllegalArgumentException exception) {
            return UNKNOWN;
        }
    }
}
