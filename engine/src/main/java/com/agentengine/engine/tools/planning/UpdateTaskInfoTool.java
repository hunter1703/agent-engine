package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class UpdateTaskInfoTool extends UpdateTaskStatusTool {
  private static final String TOOL_NAME = "update_task_info";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME, "Update fields on a specific task in the current plan.",
      Map.of());

  public UpdateTaskInfoTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
      @ToolSchema(name = "task_id", description = "ID of the specific task to update") String taskId,
      @ToolSchema(name = "name", description = "Updated task name", optional = true) String name,
      @ToolSchema(name = "goal", description = "Updated task goal", optional = true) String goal,
      @ToolSchema(name = "description", description = "Updated task description", optional = true) String description) {

    return _execute(toolContext, taskId, name, goal, description, null, null);
  }
}
