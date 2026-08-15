package com.agentengine.scheduler.api.runner;

import com.agentengine.scheduler.api.models.Job;

public interface SchedulerService {
    void schedule(Job job);

    Job getJob(String jobId);

    void cancelJob(String jobId);
}
