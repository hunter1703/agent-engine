package com.agentengine.scheduler.api.runner;

import java.util.Map;

/**
 * Everything a {@link Job} is given when it runs.
 *
 * <p>{@code previousResult} is whatever the last successful run of this job returned, which lets a
 * polling job resume from where it stopped without a side table. {@code scheduledTime} is the fire
 * time the run belongs to — paired with {@code jobId} it forms a stable idempotency key a job can
 * hand to whatever it calls.
 */
public record JobContext(
    String jobId, Map<String, Object> jobData, Map<String, Object> previousResult) {

  public JobContext {
    jobData = jobData == null ? Map.of() : Map.copyOf(jobData);
    previousResult = previousResult == null ? Map.of() : Map.copyOf(previousResult);
  }
}
