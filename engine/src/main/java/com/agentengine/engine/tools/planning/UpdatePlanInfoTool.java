package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.SimpleTool;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public final class UpdatePlanInfoTool extends SimpleTool {
  private static final String TOOL_NAME = "update_plan_info";

  @Schema(
      name = "update_plan_info",
      description =
          "Update the general information of the current plan. toolContext is injected by the runtime.")
  public Map<String, Object> execute(
      @Schema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @Schema(name = "plan_id", description = "ID of the plan to update") String planId,
      @Schema(name = "name", description = "New name (optional)") String name,
      @Schema(name = "description", description = "New description (optional)") String description,
      @Schema(name = "expected_outcome", description = "New expected outcome (optional)")
          String expectedOutcome) {

    final Plan currentPlan = PlanningUtils.getCurrentPlan(toolContext);
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }
    if (StringUtils.isBlank(planId)) {
      return Map.of("error", "plan_id is required");
    }

    final Plan targetPlan = PlanningUtils.findPlanById(currentPlan, planId);
    if (targetPlan == null) {
      return Map.of("error", "Plan not found: " + planId);
    }

    if (name != null) {
      targetPlan.setName(name);
    }
    if (description != null) {
      targetPlan.setDescription(description);
    }
    if (expectedOutcome != null) {
      targetPlan.setExpectedOutcome(expectedOutcome);
    }

    PlanningUtils.savePlan(toolContext, currentPlan);
    return Map.of("status", "success");
  }

  @Override
  public ToolDescriptor descriptor() {
    return new ToolDescriptor(TOOL_NAME, List.of(ALL), Map.of());
  }
}
