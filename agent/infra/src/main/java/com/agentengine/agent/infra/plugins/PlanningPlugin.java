package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.agent.infra.tools.planning.PlanningValidator;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.ResponseUtils;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.Violation;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmRequest;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;

/** Requires every task of the active plan to be completed before the model gives its final answer. */
public final class PlanningPlugin extends BasePlugin {

  private final PlanningValidator planningValidator;

  public PlanningPlugin(final PlanningValidator planningValidator) {
    super("planning_plugin");
    this.planningValidator = planningValidator;
  }

  /** While tasks are open, the model's text is kept from the user until its response is complete. */
  @Override
  public Maybe<LlmResponse> beforeModelCallback(
      final CallbackContext callbackContext, final LlmRequest.Builder requestBuilder) {
    final SessionState sessionState =
        SessionUtils.getSessionState(callbackContext.invocationContext());
    if (sessionState.hasActivePlan() && !planningValidator.allowsFinalAnswer(sessionState.plan())) {
      sessionState.runState().holdBackText();
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse response) {
    if (!ResponseUtils.isFinalAnswer(response)) {
      return Maybe.empty();
    }

    final SessionState sessionState =
        SessionUtils.getSessionState(callbackContext.invocationContext());
    final RunState runState = sessionState.runState();

    if (!sessionState.hasActivePlan()) {
      return Maybe.empty();
    }

    final Plan plan = sessionState.plan();
    if (!planningValidator.allowsFinalAnswer(plan)) {
      final Task openTask = PlanningUtils.findFirstOpenTask(plan);
      final String message =
          "Cannot submit a final answer yet: %s is %s%s. Complete each open task with its outcome, one at a time in order, then give the final answer. Saying a task is done in text does not update the plan. The user has not seen that reply, so the final answer must be complete."
              .formatted(
                  PlanningUtils.planLabel(plan),
                  plan.getStatus().getValue(),
                  openTask == null
                      ? ""
                      : " and its first open task is " + PlanningUtils.describeTask(openTask));
      runState.addSignal(
          callbackContext,
          new Signal<>(
              "final_answer_validation",
              Violation.builder("final_answer_validation").message(message).build(),
              true));
    }
    return Maybe.empty();
  }
}
