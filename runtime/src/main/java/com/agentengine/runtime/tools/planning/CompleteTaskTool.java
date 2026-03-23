package com.agentengine.runtime.tools.planning;

import com.agentengine.runtime.api.tools.ToolDescriptor;
import com.agentengine.runtime.plugin.annotations.ToolSchema;
import com.agentengine.runtime.tools.planning.beans.Plan;
import com.agentengine.runtime.tools.planning.beans.Task;
import com.agentengine.runtime.tools.planning.beans.TaskStatus;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CompleteTaskTool extends UpdateTaskStatusTool {
  private static final String TOOL_NAME = "complete_task";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Mark a specific task as DONE or ABANDONED with a final result.", Map.of());

  public CompleteTaskTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
      @ToolSchema(name = "task_id", description = "ID of the specific task to complete") String taskId,
      @ToolSchema(name = "status", description = "The terminal status of the task", enums = {"done", "abandoned"}) String status,
      @ToolSchema(name = "result", description = "The actual result of the task") String result) {
    final TaskStatus newStatus = TaskStatus.valueOfOrDefault(status);
    if (newStatus == TaskStatus.UNKNOWN || !PlanningUtils.isTerminalStatus(newStatus)) {
      return Map.of("error",
          "Only terminal statuses (" + PlanningUtils.getTerminalStatuses(TaskStatus.class) + ") are allowed. Found: " + status);
    }
    final Map<String, Object> response = CollectionUtils
        .nullSafeMutableMap(_execute(toolContext, taskId, null, null, null, newStatus, result));
    final Plan currentPlan = RunUtils.getOrInitState(toolContext.invocationContext()).plan();
    final Task nextTask = PlanningUtils.findNextTodoTask(currentPlan);
    if (nextTask != null) {
      response.put("next_task", "Next recommended task: [" + nextTask.getTaskId() + "] (" + nextTask.getName() + ")");
    } else {
      response.put("next_task", "No more pending tasks. You may want to finish the plan.");
    }

    return response;
  }
}
