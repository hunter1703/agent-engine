package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CompleteTaskTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.COMPLETE_TASK,
          """
          Finishes a task of the active plan with its outcome, and starts the next one. A task made \
          of parts can only be completed once its parts are; abandoning it abandons its open parts \
          too. The plan finishes with its last task.
          Returns: { next_task } naming the task now in progress, { plan_finished: true } when that was the last one, or { error } on failure.""",
          Map.of());

  private final PlanningValidator planningValidator;
  public CompleteTaskTool(final PlanningValidator planningValidator) {
    super(DESCRIPTOR);
    this.planningValidator = planningValidator;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(name = Constants.ToolArgs.TASK, description = "Name of the task to complete in the active plan.")
          final String task,
      @ToolArg(
          name = Constants.ToolArgs.TASK_RESULT,
          description = "Concise description of the outcome and key findings for this completed task.")
          final String result,
      @ToolArg(
          name = Constants.ToolArgs.TASK_STATUS,
          description =
              """
              'done' for a task carried out, 'abandoned' for one no longer needed. Defaults to 'done'.""",
          enums = {"done", "abandoned"},
          optional = true)
          final String status) {
    if (StringUtils.isBlank(task)) {
      return ToolOutput.direct(Map.of("error", "Task name is required."));
    }
    if (StringUtils.isBlank(result)) {
      return ToolOutput.direct(Map.of("error", "Result is required when completing a task."));
    }

    final TaskStatus newStatus;
    if (StringUtils.isNotBlank(status)) {
      newStatus = TaskStatus.valueOfOrDefault(status);
      if (newStatus != TaskStatus.DONE && newStatus != TaskStatus.ABANDONED) {
        return ToolOutput.direct(
            Map.of("error", "Invalid status: " + status + ". Must be 'done' or 'abandoned'."));
      }
    } else {
      newStatus = TaskStatus.DONE;
    }

    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan currentPlan = sessionState.plan();
    if (currentPlan == null) {
      return ToolOutput.direct(Map.of("error", "No active plan found"));
    }

    final Task existingTask = PlanningUtils.findTaskByName(currentPlan, task);
    if (existingTask == null) {
      return ToolOutput.direct(Map.of("error", "Task not found: " + task));
    }

    final String validationError = planningValidator.canUpdateTask(currentPlan, existingTask, newStatus);
    if (validationError != null) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    Plan updatedPlan =
        PlanningUtils.applyTaskUpdate(currentPlan, existingTask.getName(), newStatus, result);

    final Map<String, Object> response = new LinkedHashMap<>();
    if (updatedPlan.getStatus().isTerminal()) {
      response.put("plan_finished", true);
    } else {
      final List<Task> activeLineage = PlanningUtils.activateNextActionableLineage(updatedPlan);
      if (activeLineage.isEmpty()) {
        response.put(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS);
      } else {
        response.put("next_task", activeLineage.getLast().getName());
      }
    }

    sessionState.updatePlan(updatedPlan);
    return ToolOutput.direct(response);
  }
}
