package com.agentengine.tenancy.jobs;

import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;
import com.agentengine.tenancy.AccessControlService;

/**
 * Has tenancy submit again the customer's role mappings left pending for too long. Scheduled for
 * every customer when it is provisioned.
 */
public final class ResubmitStaleRoleMappingsJob extends Job {

  public ResubmitStaleRoleMappingsJob(final JobContext context) {
    super(context);
  }

  @Override
  public JobResult run() {
    service(AccessControlService.class).resubmitStaleRoleMappings();
    return JobResult.empty();
  }
}
