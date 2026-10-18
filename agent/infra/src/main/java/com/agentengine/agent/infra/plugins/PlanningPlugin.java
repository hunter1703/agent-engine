package com.agentengine.agent.infra.plugins;

import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.agent.infra.tools.planning.PlanningUtils;
import com.agentengine.agent.infra.utils.*;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.ContentUtils;
import com.agentengine.util.agents.beans.Signal;
import com.agentengine.util.common.Violation;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.agents.CallbackContext;
import com.google.adk.models.LlmResponse;
import com.google.adk.plugins.BasePlugin;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.ToolContext;
import io.reactivex.rxjava3.core.Maybe;
import jakarta.inject.Inject;
import java.util.Map;

import static com.agentengine.agent.infra.tools.planning.PlanningUtils.*;
import static com.agentengine.agent.infra.tools.planning.PlanningValidator.*;

/**
 * Enforces planning state machine and task lifecycle during agent execution:
 *
 * <p>1. Once a plan is active, domain tools may only be executed when a task is currently
 * in_progress. If no task is in_progress, only planning tools are permitted.
 *
 * <p>2. Intermediate visible text emitted alongside tool calls during plan execution is treated as
 * internal reasoning/drafting and converted to thought so it does not leak premature output.
 *
 * <p>3. Final answer responses require all tasks in the active plan to be completed.
 */
public final class PlanningPlugin extends BasePlugin {

  public PlanningPlugin() {
    super("planning_plugin");
  }

  @Override
  public Maybe<Map<String, Object>> beforeToolCallback(
      final BaseTool tool, final Map<String, Object> args, final ToolContext toolContext) {
    if (tool == null || StringUtils.isBlank(tool.name())) {
      return Maybe.empty();
    }
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    if (!sessionState.hasActivePlan()) {
      return Maybe.empty();
    }
    final String toolName = tool.name();
    if (Constants.ToolNames.isPlanningTool(toolName)
        || Constants.ToolNames.REFRESH_REMINDERS.equals(toolName)) {
      return Maybe.empty();
    }
    final Plan plan = sessionState.plan();
    if (plan.getNextTaskWithStatus(TaskStatus.IN_PROGRESS) == null) {
      final Task nextTask = plan.getNextTaskWithStatus(TaskStatus.TODO);
      final String nextTaskHint =
          nextTask != null ? " (e.g. task '" + nextTask.getName() + "')" : "";
      return Maybe.just(
          Map.of(
              "error",
              "Cannot execute tool '"
                  + toolName
                  + "': active plan '"
                  + plan.getTitle()
                  + "' has no task currently in_progress. You must call "
                  + Constants.ToolNames.COMPLETE_TASK
                  + " to complete the active task before executing domain tools."));
    }
    return Maybe.empty();
  }

  @Override
  public Maybe<LlmResponse> afterModelCallback(
      final CallbackContext callbackContext, final LlmResponse response) {
    if (response == null || response.partial().orElse(false)) {
      return Maybe.empty();
    }

    final SessionState sessionState =
        SessionUtils.getSessionState(callbackContext.invocationContext());
    final RunState runState = sessionState.runState();

    if (!sessionState.hasActivePlan() || runState.isNoteStarted()) {
      return Maybe.empty();
    }

    final boolean hasVisibleText = ContentUtils.hasVisibleText(response.content().orElse(null));
    if (!hasVisibleText) {
      return Maybe.empty();
    }

    final Plan plan = sessionState.plan();
    final String planViolation = getPrematureCompleteViolation(plan);
    if (StringUtils.isNotBlank(planViolation)) {
      runState.addSignal(
          callbackContext,
          new Signal<>(
              "final_answer_validation",
              Violation.builder("final_answer_validation").message(planViolation).build(),
              true));
    }
    return Maybe.empty();
  }

  private static String getPrematureCompleteViolation(final Plan plan) {
    if (plan == null) {
      return null;
    }
    if (!PlanningUtils.isTerminalStatus(plan.getStatus())) {
      final Task openTask = findFirstOpenTask(plan);
      final String openTaskMessage =
              openTask == null ? "" : " and has open tasks (e.g., " + describeTask(openTask) + ")";
      return "Cannot submit final answer while " + planLabel(plan) + " is "
              + plan.getStatus().getValue() + openTaskMessage
              + ". You must call the " + Constants.ToolNames.COMPLETE_TASK
              + " tool to complete all open tasks (with a result) before submitting the final answer."
              + " Stating completion in text does not update the plan.";
    }
    return null;
  }
}
