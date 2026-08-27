package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Enforces plan/task completion before final-answer responses.
 *
 * <p>Validates that any final-answer response corresponds to a completed plan. When a final answer
 * is signaled while tasks remain open, the response is rejected and continuation is requested so
 * the model can continue working toward completion.
 */
public final class PlanningPlugin extends BasePlugin {

  public PlanningPlugin() {
    super("planning_plugin");
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse response) {

    if (response.partial().orElse(false)) {
      return Maybe.empty();
    }

    final SessionState sessionState =
        SessionUtils.getSessionState(callbackContext.invocationContext());
    final RunState runState = sessionState.runState();

    if (!sessionState.hasActivePlan()) {
      return Maybe.empty();
    }

    if (!ResponseUtils.isFinalAnswer(response)) {
      return Maybe.empty();
    }

    final Plan plan = sessionState.plan();
    final String planViolation = PlanningValidator.getPrematureCompleteViolation(plan);
    if (StringUtils.isBlank(planViolation)) {
      return Maybe.empty();
    }
    runState.addSignal(
        new Signal<>(
            "final_answer_validation",
            Violation.builder("final_answer_validation").message(planViolation).build(),
            true));
    return Maybe.empty();
  }
}
