package com.agentengine.tenancy.jobs;

import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;
import com.agentengine.tenancy.RoleService;

/**
 * Has tenancy submit again the customer's roles left pending for too long. Scheduled for every
 * customer when it is provisioned.
 */
public final class ResubmitStaleRolesJob extends Job {

  public ResubmitStaleRolesJob(final JobContext context) {
    super(context);
  }

  @Override
  public JobResult run() {
    service(RoleService.class).resubmitStaleRoles();
    return JobResult.empty();
  }
}
