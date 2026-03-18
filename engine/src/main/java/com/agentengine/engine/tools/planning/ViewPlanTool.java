package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.annotations.ToolSchema;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunUtils;
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
