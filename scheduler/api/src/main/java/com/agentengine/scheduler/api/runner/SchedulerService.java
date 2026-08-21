package com.agentengine.scheduler.api.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.ms.client.MicroService;

@MicroService("scheduler")
public interface SchedulerService {
  String schedule(JobDefinition jobDefinition);

  JobDefinition getJob(String jobId);

  PaginatedResult<JobDefinition> findJobs(Query query);

  void cancelJob(String jobId);
}
