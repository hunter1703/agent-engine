package com.agentengine.agent.jobs;

import com.agentengine.agent.api.model.UserMessage;
import com.agentengine.agent.api.services.RuntimeService;
import com.agentengine.scheduler.api.runner.Job;
import com.agentengine.scheduler.api.runner.JobContext;
import com.agentengine.scheduler.api.runner.JobResult;
import com.agentengine.util.common.CollectionUtils;
import java.util.Map;

/**
 * Starts one agent run per firing. Required payload fields: {@code agentId} and {@code message}.
 *
 * <p>Session continuity across firings is opt in, in one of two ways: an explicit {@code sessionId}
 * payload field always wins; otherwise, a {@code singletonSession} payload flag makes every firing
 * after the first reuse the session id the first firing generated, carried forward via {@link
 * JobContext#previousResult()} — the scheduler's existing mechanism for a job to remember state
 * between runs, rather than a value fixed once at schedule time. With neither set, each firing gets
 * its own fresh session.
 *
 * <p>Does not wait for the run to finish: {@link RuntimeService#invoke} returns as soon as the run
 * is accepted, so a successful {@link #run()} confirms only that, not that the run completed.
 */
public final class InvokeAgentJob extends Job {

  private static final String AGENT_ID_KEY = "agentId";
  private static final String MESSAGE_KEY = "message";
  private static final String SESSION_ID_KEY = "sessionId";
  private static final String SINGLETON_SESSION_KEY = "singletonSession";

  private final String agentId;
  private final String message;
  private final String sessionId;

  public InvokeAgentJob(final JobContext context) {
    super(context);
    this.agentId = requireField(AGENT_ID_KEY);
    this.message = requireField(MESSAGE_KEY);
    this.sessionId = getSessionId();
  }

  @Override
  public JobResult run() {
    final String invokedSessionId =
        service(RuntimeService.class).invoke(agentId, sessionId, UserMessage.ofText(message));
    return JobResult.of(Map.of(SESSION_ID_KEY, invokedSessionId));
  }

  private String getSessionId() {
    if (!Boolean.TRUE.equals(context.jobData().get(SINGLETON_SESSION_KEY))) {
      return null;
    }
    return CollectionUtils.getStringValueFromMap(context.previousResult(), SESSION_ID_KEY);
  }
}
