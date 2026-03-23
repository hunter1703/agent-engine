package com.agentengine.runtime.tools.planning;

import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.planning.beans.TaskStatus;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class StartTaskTool extends UpdateTaskStatusTool {
  private static final String TOOL_NAME = "start_task";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "Mark a specific task as IN_PROGRESS to begin working on it.", Map.of());

  public StartTaskTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
      @ToolSchema(name = "task_id", description = "ID of the specific task to start") String taskId) {
    return _execute(toolContext, taskId, null, null, null, TaskStatus.IN_PROGRESS, null);
  }
}
