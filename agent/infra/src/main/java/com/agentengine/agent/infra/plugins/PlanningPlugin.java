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
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import io.reactivex.rxjava3.core.Maybe;

/**
 * Keeps the run going while the active plan has open tasks: a reply without tool calls does not end
 * it. The reply is shown to the user like any other, and the model is told to complete the tasks.
 */
public final class PlanningPlugin extends BasePlugin {

  private final PlanningValidator planningValidator;

  public PlanningPlugin(final PlanningValidator planningValidator) {
    super("planning_plugin");
    this.planningValidator = planningValidator;
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
          "Your reply was shown to the user, but %s is %s%s. Complete each open task with its outcome, one at a time in order; saying a task is done in text does not update the plan."
              .formatted(
                  PlanningUtils.planLabel(plan),
                  plan.getStatus().getValue(),
                  openTask == null
                      ? ""
                      : " and its first open task is " + PlanningUtils.describeTask(openTask));
      runState.addSignal(callbackContext, new Signal<>("plan_has_open_tasks", message, true));
    }
    return Maybe.empty();
  }
}
