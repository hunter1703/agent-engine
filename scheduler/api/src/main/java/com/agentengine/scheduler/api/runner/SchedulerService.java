package com.agentengine.scheduler.api.runner;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.util.ms.client.MicroService;

@MicroService("scheduler")
public interface SchedulerService {
    void schedule(JobDefinition jobDefinition);

    JobDefinition getJob(String jobId);

    void cancelJob(String jobId);
}
