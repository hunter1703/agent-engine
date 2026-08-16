package com.agentengine.scheduler.api.runner;

import java.util.Map;

/**
 * What a {@link Job} reports back when it finishes.
 *
 * <p>{@code data} is handed to the next run as {@link JobContext#previousResult()}, which lets a
 * polling job resume from where it stopped. It is a record rather than a bare map so that richer
 * outcomes — a job declaring itself finished and needing no further scheduling, or asking for a
 * specific next fire time — can be added without changing every implementation.
 */
public record JobResult(Map<String, Object> data) {

    public JobResult {
        data = data == null ? Map.of() : Map.copyOf(data);
    }

    public static JobResult empty() {
        return new JobResult(Map.of());
    }

    public static JobResult of(final Map<String, Object> data) {
        return new JobResult(data);
    }
}
