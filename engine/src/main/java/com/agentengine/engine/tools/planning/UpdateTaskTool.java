package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.tools.planning.beans.TaskStatus;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public final class UpdateTaskTool extends Tool {
  private static final String TOOL_NAME = "update_task";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Update fields on a specific task in the current plan.",
          List.of(ALL),
          Map.of());

  public UpdateTaskTool() {
    super(DESCRIPTOR);
  }


  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(name = "task_id", description = "ID of the specific task to update") String taskId,
      @ToolSchema(
              name = "name",
              description = "Updated task name",
              optional = true)
          String name,
      @ToolSchema(
              name = "goal",
              description = "Updated task goal",
              optional = true)
          String goal,
      @ToolSchema(
              name = "description",
              description = "Updated task description",
              optional = true)
          String description,
      @ToolSchema(
              name = "status",
              description = "The new status of the task",
              enums = {"todo", "in_progress", "done", "abandoned"},
              optional = true)
          String status,
      @ToolSchema(name = "result", description = "The actual result of the task", optional = true)
          String result) {

    final Plan currentPlan = PlanningUtils.getCurrentPlan(toolContext);
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }

    final Task task = PlanningUtils.findTaskById(currentPlan, taskId);
    if (task == null) {
      return Map.of("error", "Task not found with ID: " + taskId);
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
    if (StringUtils.isNotBlank(status)) {
      task.setStatus(TaskStatus.valueOf(status.toUpperCase()));
    }
    if (StringUtils.isNotBlank(result)) {
      task.setResult(result);
    }

    PlanningUtils.savePlan(toolContext, currentPlan);
    return Map.of("status", "success", "task_id", taskId);
  }

}
