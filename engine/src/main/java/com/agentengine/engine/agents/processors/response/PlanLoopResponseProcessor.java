package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.planning.PlanningValidator;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.engine.utils.Violation;
import com.agentengine.util.common.StringUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;

/**
 * Enforces plan/task completion before final-answer responses.
 *
 * <p>
 * Validates that any final-answer response corresponds to a completed plan.
 * When a final answer is signaled while tasks remain open, the response is
 * rejected and continuation is requested so the model can continue working
 * toward completion.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 * <li>Final-answer responses are allowed only when the plan is absent or
 * already completed.
 * <li>If a final answer is signaled while the plan has open tasks, a violation
 * is emitted and continuation is requested (the model regenerates without
 * finalizing).
 * <li>Partial responses and tool-call responses pass through unchanged.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 * <li>Session plan state must be initialized in
 * {@code RunUtils.getOrInitState(context)}.
 * <li>Prior processors have completed their modifications to the response.
 * </ul>
 */
public final class PlanLoopResponseProcessor implements ResponseProcessor {
  public static final PlanLoopResponseProcessor INSTANCE = new PlanLoopResponseProcessor();

  private PlanLoopResponseProcessor() {
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return ResponseUtils.single(response);
    }

    final RunState runState = RunUtils.getOrInitState(context);
    if (!runState.hasActivePlan()) {
      return ResponseUtils.single(response);
    }

    final Plan plan = runState.plan();

    if (!ResponseUtils.isFinalAnswer(response)) {
      return ResponseUtils.single(response);
    }

    final String planViolation = PlanningValidator.getPrematureCompleteViolation(plan);
    if (StringUtils.isBlank(planViolation)) {
      return ResponseUtils.single(response);
    }
    runState.addViolation(Violation.builder("final_answer_validation").message(planViolation).correctionMessage(planViolation).build());
    runState.requestContinuation();
    return ResponseUtils.single(response);
  }
}
