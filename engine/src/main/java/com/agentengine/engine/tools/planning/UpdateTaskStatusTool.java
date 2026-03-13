package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public abstract class UpdateTaskStatusTool extends Tool {

  protected UpdateTaskStatusTool(final ToolDescriptor toolDescriptor) {
    super(toolDescriptor);
  }

  protected Map<String, Object> _execute(final ToolContext toolContext, final String taskId, final String name, final String goal,
      final String description, TaskStatus newStatus, String result) {
    final RunState runState = RunUtils.getState(toolContext.invocationContext());
    final Plan currentPlan = runState.plan();
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }

    final Task task = PlanningUtils.findTaskById(currentPlan, taskId);
    if (task == null) {
      return Map.of("error", "Task not found with ID: " + taskId);
    }

    newStatus = newStatus != null ? newStatus : task.getStatus();
    result = StringUtils.isNotBlank(result) ? result : task.getResult();
    final String validationError = currentPlan.canUpdateTask(task, newStatus, result);
    if (validationError != null) {
      return Map.of("error", validationError);
    }

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
    runState.updatePlan(currentPlan, toolContext);
    return Map.of("status", "success", "task_id", taskId, "new_status", newStatus.getValue());
  }
}
