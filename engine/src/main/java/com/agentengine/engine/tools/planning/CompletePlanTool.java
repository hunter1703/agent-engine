package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.SimpleTool;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public final class CompletePlanTool extends SimpleTool {
  private static final String TOOL_NAME = "finish_plan";

  @Override
  public ToolDescriptor descriptor() {
    return new ToolDescriptor(
        TOOL_NAME,
        List.of(ALL),
        Map.of());
  }

  @Schema(
      name = "finish_plan",
      description =
          "Mark the entire plan as complete or abandoned with a final outcome. toolContext is injected by the runtime.")
  public Map<String, Object> execute(
      @Schema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @Schema(name = "plan_id", description = "ID of the plan to finish") String planId,
      @Schema(name = "status", description = "Final status: DONE or ABANDONED") String statusStr,
      @Schema(
              name = "outcome",
              description = "Summary of what was accomplished or why it was abandoned")
          String outcome) {

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

    try {
      targetPlan.finish(PlanStatus.valueOf(statusStr.toUpperCase()), outcome);
      PlanningUtils.savePlan(toolContext, currentPlan);
      return Map.of("status", "success");
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid status: " + statusStr);
    }
  }
}
