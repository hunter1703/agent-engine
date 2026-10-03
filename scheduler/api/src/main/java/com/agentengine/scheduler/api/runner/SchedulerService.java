package com.agentengine.scheduler.api.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;

@MicroService("scheduler")
public interface SchedulerService {
  /**
   * Creates the job, or replaces the stored one while it is still at the definition's version, and
   * schedules its next run as the caller.
   */
  String schedule(JobDefinition jobDefinition);

  /** {@link #schedule}, whatever version the stored job is at: sets the job to the definition. */
  String scheduleIgnoringVersion(JobDefinition jobDefinition);

  JobDefinition getJob(String jobId);

  PaginatedResult<JobDefinition> findJobs(Query query);

  /** Deletes the job and cancels its pending triggers. False if there was no such job. */
  boolean cancelJob(String jobId);
}
