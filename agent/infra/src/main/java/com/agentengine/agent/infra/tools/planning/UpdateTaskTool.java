package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
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
import java.util.Map;

public final class UpdateTaskTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.UPDATE_TASK,
          """
          Updates a task's status in the active plan to 'in_progress', 'done', or 'abandoned'. \
          When starting work, set status to 'in_progress'. When finished, set status to 'done' (or \
          'abandoned') and provide the required outcome result. When the final pending task is closed, \
          the plan is automatically marked finished. \
          Returns: { status: "success", task, new_status, next_task?, plan_finished?, plan_status? } on success, or { error } on failure.""",
          Map.of());

  public UpdateTaskTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(
              name = "task",
              description = "Name of the task to update in the active plan.")
          final String task,
      @ToolArg(
              name = "status",
              description =
                  """
                  New status for the task. 'in_progress' signals work has begun; 'done' indicates \
                  successful completion; 'abandoned' indicates the task was skipped or cancelled. \
                  Terminal statuses (done, abandoned) are permanent.""",
              enums = {"in_progress", "done", "abandoned"})
          final String status,
      @ToolArg(
              name = "result",
              description =
                  """
                  Concise description of the outcome. Required when status is 'done' or 'abandoned'; \
                  optional when status is 'in_progress'.""",
              optional = true)
          final String result) {
    final TaskStatus newStatus = TaskStatus.valueOfOrDefault(status);
    if (newStatus == TaskStatus.UNKNOWN || newStatus == TaskStatus.TODO) {
      return ToolOutput.direct(
          Map.of(
              "error",
              "Invalid status: "
                  + status
                  + ". Must be one of: in_progress, done, abandoned"));
    }

    if (PlanningUtils.isTerminalStatus(newStatus) && StringUtils.isBlank(result)) {
      return ToolOutput.direct(
          Map.of("error", "Result is required when marking a task as " + status + "."));
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

    final String validationError = currentPlan.canUpdateTask(existingTask, newStatus, result);
    if (validationError != null) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    final Plan updatedPlan =
        PlanningUtils.applyTaskUpdate(currentPlan, existingTask.getName(), newStatus, result);
    sessionState.updatePlan(updatedPlan);

    final Map<String, Object> response = new LinkedHashMap<>();
    response.put(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS);
    response.put("task", existingTask.getName());
    response.put("new_status", newStatus.getValue());

    if (updatedPlan.isFinished()) {
      response.put("plan_finished", true);
      response.put("plan_status", updatedPlan.getStatus().getValue());
    } else if (PlanningUtils.isTerminalStatus(newStatus)) {
      final Task nextTask = PlanningUtils.findNextActionableTask(updatedPlan, existingTask);
      if (nextTask != null) {
        response.put("next_task", nextTask.getName());
      }
    }

    return ToolOutput.direct(response);
  }
}
