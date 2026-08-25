package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public abstract class UpdateTaskStatusTool extends Tool {

  protected UpdateTaskStatusTool(final ToolDescriptor toolDescriptor) {
    super(toolDescriptor);
  }

  protected Map<String, Object> _execute(
      final ToolContext toolContext,
      final String taskId,
      final String name,
      final String goal,
      final String description,
      final TaskStatus newStatus,
      final String result) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan currentPlan = sessionState.plan();
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }

    final Task task = PlanningUtils.findTaskById(currentPlan, taskId);
    if (task == null) {
      return Map.of("error", "Task not found with ID: " + taskId);
    }
    final TaskStatus resolvedStatus = newStatus != null ? newStatus : task.getStatus();
    final String resolvedResult = StringUtils.isNotBlank(result) ? result : task.getResult();
    final String validationError = currentPlan.canUpdateTask(task, resolvedStatus, resolvedResult);
    if (validationError != null) {
      return Map.of("error", validationError);
    }

    final Plan updatedPlan =
        applyTaskUpdate(currentPlan, taskId, name, goal, description, newStatus, result);
    sessionState.updatePlan(updatedPlan);
    return Map.of("status", "success", "task_id", taskId, "new_status", resolvedStatus.getValue());
  }

  public static Plan applyTaskUpdate(
      final Plan plan,
      final String taskId,
      final String name,
      final String goal,
      final String description,
      TaskStatus newStatus,
      String result) {
    final Plan updatedPlan = new Plan(plan);
    final Task task = PlanningUtils.findTaskById(updatedPlan, taskId);
    if (task == null) {
      return updatedPlan;
    }
    newStatus = newStatus != null ? newStatus : task.getStatus();
    result = StringUtils.isNotBlank(result) ? result : task.getResult();
    if (StringUtils.isNotBlank(name)) {
      task.setName(name);
    }
    if (StringUtils.isNotBlank(goal)) {
      task.setGoal(goal);
    }
    if (StringUtils.isNotBlank(description)) {
      task.setDescription(description);
    }
    task.setStatus(newStatus);
    task.setResult(result);
    return updatedPlan;
  }
}
