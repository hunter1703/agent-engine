package com.agentengine.agent.infra.agents.processors.response;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Enforces plan/task completion before final-answer responses.
 *
 * <p>Validates that any final-answer response corresponds to a completed plan. When a final answer
 * is signaled while tasks remain open, the response is rejected and continuation is requested so
 * the model can continue working toward completion.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 *   <li>Final-answer responses are allowed only when the plan is absent or already completed.
 *   <li>If a final answer is signaled while the plan has open tasks, a violation is emitted and
 *       continuation is requested (the model regenerates without finalizing).
 *   <li>Partial responses and tool-call responses pass through unchanged.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 *   <li>Session state must be initialized in {@code SessionUtils.initSessionState(context)}.
 *   <li>Prior processors have completed their modifications to the response.
 * </ul>
 */
public final class PlanLoopResponseProcessor implements ResponseProcessor {
  public static final PlanLoopResponseProcessor INSTANCE = new PlanLoopResponseProcessor();

  private PlanLoopResponseProcessor() {}

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return ResponseUtils.single(response);
    }

    final SessionState sessionState = SessionUtils.getSessionState(context);
    final RunState runState = sessionState.runState();

    if (!sessionState.hasActivePlan()) {
      return ResponseUtils.single(response);
    }

    if (!ResponseUtils.isFinalAnswer(response)) {
      return ResponseUtils.single(response);
    }

    final Plan plan = sessionState.plan();
    final String planViolation = PlanningValidator.getPrematureCompleteViolation(plan);
    if (StringUtils.isBlank(planViolation)) {
      return ResponseUtils.single(response);
    }
    runState.requestContinuation(
        Violation.builder("final_answer_validation").message(planViolation).build());
    return ResponseUtils.single(response);
  }
}
