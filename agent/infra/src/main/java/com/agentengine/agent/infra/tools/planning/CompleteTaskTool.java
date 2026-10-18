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
          Marks a task in the active plan as completed ('done' or 'abandoned') with a concise outcome result. \
          Completing a task automatically marks the next actionable task (and its parent lineage if nested) \
          as 'in_progress'. When the final task is closed, the active plan is automatically marked finished. \
          All tasks must be completed before delivering a final answer. \
          Returns: { status: "success", completed_task, new_status, next_task?, active_lineage?, plan_finished?, message } on success, or { error } on failure.""",
          Map.of());

  private final PlanningValidator planningValidator;
  public CompleteTaskTool(final PlanningValidator planningValidator) {
    super(DESCRIPTOR);
    this.planningValidator = planningValidator;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(name = "task", description = "Name of the task to complete in the active plan.")
          final String task,
      @ToolArg(
          name = "result",
          description = "Concise description of the outcome and key findings for this completed task.")
          final String result,
      @ToolArg(
          name = "status",
          description =
              """
              Terminal status for the task. 'done' indicates successful completion; 'abandoned' \
              indicates skipped or cancelled. Defaults to 'done'.""",
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

    final String validationError = planningValidator.canUpdateTask(currentPlan, existingTask, newStatus, result);
    if (validationError != null) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    Plan updatedPlan =
        PlanningUtils.applyTaskUpdate(currentPlan, existingTask.getName(), newStatus, result);

    final Map<String, Object> response = new LinkedHashMap<>();
    response.put(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS);
    response.put("completed_task", existingTask.getName());
    response.put("new_status", newStatus.getValue());

    if (updatedPlan.getStatus().isTerminal()) {
      response.put("plan_finished", true);
      response.put("plan_status", updatedPlan.getStatus().getValue());
      response.put(
          "message",
          "Task '"
              + existingTask.getName()
              + "' completed. All tasks finished! Active plan is complete.");
    } else {
      final List<Task> activeLineage =
          PlanningUtils.activateNextActionableLineage(updatedPlan);
      if (!activeLineage.isEmpty()) {
        final Task nextTask = activeLineage.getLast();
        final List<String> activeLineageNames = PlanningUtils.taskNames(activeLineage);
        response.put("next_task", nextTask.getName());
        response.put("active_lineage", activeLineageNames);
        final String lineageStr = String.join(" > ", activeLineageNames);
        response.put(
            "message",
            "Task '"
                + existingTask.getName()
                + "' completed. Next active task is '"
                + nextTask.getName()
                + "' (active branch: "
                + lineageStr
                + ").");
      } else {
        response.put(
            "message",
            "Task '"
                + existingTask.getName()
                + "' completed.");
      }
    }

    sessionState.updatePlan(updatedPlan);
    return ToolOutput.direct(response);
  }
}
