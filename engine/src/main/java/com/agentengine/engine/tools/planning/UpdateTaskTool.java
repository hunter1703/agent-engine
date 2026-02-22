package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.beans.session.PlanStatus;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;

import java.util.List;
import java.util.Map;

public final class UpdateTaskTool implements Tool {
  private static final String TOOL_NAME = "update_subtask_state";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, List.of(ALL), Map.of());

  @Schema(
      name = "update_subtask_state",
      description =
          "Update the status of a specific subtask. Use this to mark tasks as in progress, done, or abandoned. toolContext is injected by the runtime.")
  public Map<String, Object> execute(
      @Schema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @Schema(name = "plan_id", description = "ID of the plan to update") String planId,
      @Schema(name = "status", description = "New status: TODO, IN_PROGRESS, DONE, or ABANDONED")
          String statusStr,
      @Schema(
              name = "outcome",
              description = "Optional outcome description (recommended when marking DONE)",
              optional = true)
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
      final PlanStatus status = PlanStatus.valueOf(statusStr.toUpperCase());
      targetPlan.setStatus(status);

      if (status == PlanStatus.DONE && outcome != null && !outcome.isBlank()) {
        targetPlan.setOutcome(outcome);
      }

      PlanningUtils.savePlan(toolContext, currentPlan);
      return Map.of("status", "success");
    } catch (IllegalArgumentException e) {
      return Map.of("error", "Invalid status: " + statusStr);
    }
  }

  @Override
  public ToolDescriptor descriptor() {
    return DESCRIPTOR;
  }
}
