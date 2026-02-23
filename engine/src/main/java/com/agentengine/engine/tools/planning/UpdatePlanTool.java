package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

public final class UpdatePlanTool extends Tool {
  private static final String TOOL_NAME = "update_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME, "Update the general information of the current plan.", List.of(ALL), Map.of());

  public UpdatePlanTool() {
    super(DESCRIPTOR);
  }


  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(name = "title", description = "New title for the plan (optional)", optional = true)
          String title,
      @ToolSchema(name = "goal", description = "New goal for the plan (optional)", optional = true)
          String goal) {

    final Plan currentPlan = PlanningUtils.getCurrentPlan(toolContext);
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }

    if (StringUtils.isNotBlank(title)) {
      currentPlan.setTitle(title);
    }
    if (StringUtils.isNotBlank(goal)) {
      currentPlan.setGoal(goal);
    }

    PlanningUtils.savePlan(toolContext, currentPlan);
    return Map.of("status", "success");
  }
}
