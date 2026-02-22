package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public final class ViewPlanTool implements Tool {
  private static final String TOOL_NAME = "view_current_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, List.of(ALL), Map.of());

  @Schema(
      name = "view_current_plan",
      description =
          "View the complete plan including all subtasks, their statuses, and progress. toolContext is injected by the runtime.")
  public Plan viewCurrentPlan(
      @Schema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext) {
    return PlanningUtils.getCurrentPlan(toolContext);
  }

  @Override
  public ToolDescriptor descriptor() {
    return DESCRIPTOR;
  }
}
