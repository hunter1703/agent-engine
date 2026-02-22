package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.tools.SimpleTool;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public final class RevisePlanTool extends SimpleTool {
  private static final String TOOL_NAME = "revise_current_plan";

  @Schema(
      name = "revise_current_plan",
      description =
          "Replace the current plan's subtasks with a new list. Use this to restructure your plan or add/remove subtasks. toolContext is injected by the runtime.")
  public Map<String, Object> execute(
      @Schema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @Schema(name = "plan_id", description = "ID of the plan to revise") String planId,
      @Schema(
              name = "subtasks",
              description =
                  "Complete new list of subtasks (replaces existing). Can include nested subtasks.")
          List<Map<String, Object>> subtasksArgs) {

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

    final List<Plan> subtaskPlans = PlanningUtils.toPlans(subtasksArgs);
    targetPlan.setSubtasks(subtaskPlans);

    PlanningUtils.savePlan(toolContext, currentPlan);
    return Map.of("status", "success", "subtask_count", subtaskPlans.size());
  }

  @Override
  public ToolDescriptor descriptor() {
    return new ToolDescriptor(TOOL_NAME, List.of(ALL), Map.of());
  }
}
