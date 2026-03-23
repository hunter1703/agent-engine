package com.agentengine.runtime.tools.planning;

import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.tools.planning.beans.Plan;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class ViewPlanTool extends Tool {
  private static final String TOOL_NAME = "view_plan";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME,
      "View the complete plan including all tasks, their statuses, and results.", Map.of());

  public ViewPlanTool() {
    super(DESCRIPTOR);
  }

  public Plan execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext) {
    return RunUtils.getOrInitState(toolContext.invocationContext()).plan();
  }
}
