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
 * Enforces plan/task completion before the final answer.
 *
 * <p>
 * Responsibilities: - Reject final-answer signals when the plan is incomplete.
 * - Surface active task status violations on non-tool turns. - Strip invalid
 * final-answer signals when plan validation fails.
 *
 * <p>
 * Ownership: plan/task completion validation and enforcement.
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

    final RunState runState = RunUtils.getState(context);
    final Plan plan = runState.plan();

    if (plan == null || plan.getStatus().isTerminal()) {
      // no plan or completed plan
      return ResponseUtils.single(response);
    }

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
