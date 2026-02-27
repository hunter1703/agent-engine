package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

public final class ViewPlanTool extends Tool {
  private static final String TOOL_NAME = "view_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "View the complete plan including all tasks, their statuses, and results.",
          List.of(ALL),
          Map.of());

  public ViewPlanTool() {
    super(DESCRIPTOR);
  }

  public Plan execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext) {
    return RunStateUtils.getState(toolContext.invocationContext()).plan();
  }
}
