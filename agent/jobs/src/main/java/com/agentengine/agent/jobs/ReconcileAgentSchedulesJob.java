package com.agentengine.agent.jobs;

import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;
import java.util.Map;

/**
 * Periodically reconciles the user-facing {@code AgentSchedule} entities in the repository with the
 * actual {@code JobDefinition}s in the scheduler, ensuring they stay in sync in case a two-phase
 * write fails.
 */
public final class ReconcileAgentSchedulesJob extends Job {

  private static final String LAST_EXECUTION_TIME_KEY = "lastExecutionTime";

  public ReconcileAgentSchedulesJob(final JobContext context) {
    super(context);
  }

  @Override
  public JobResult run() {
    long lastExecutionTime = 0L;
    if (context.previousResult() != null
        && context.previousResult().containsKey(LAST_EXECUTION_TIME_KEY)) {
      lastExecutionTime =
          ((Number) context.previousResult().get(LAST_EXECUTION_TIME_KEY)).longValue();
    }

    final long currentExecutionTime = System.currentTimeMillis();
    service(RuntimeService.class).reconcileSchedules(lastExecutionTime);

    return JobResult.of(Map.of(LAST_EXECUTION_TIME_KEY, currentExecutionTime));
  }
}
