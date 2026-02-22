package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.beans.session.Plan;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.Annotations.Schema;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreatePlanTool implements Tool {
  private static final Logger LOG = LoggerFactory.getLogger(CreatePlanTool.class);
  private static final String TOOL_NAME = "create_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(TOOL_NAME, List.of(ALL), Map.of());

  @Override
  public ToolDescriptor descriptor() {
    return DESCRIPTOR;
  }

  @Schema(
      name = "create_plan",
      description =
          "Create a new plan with subtasks to organize your work. Each subtask can have nested subtasks for hierarchical planning. toolContext is injected by the runtime.")
  public Map<String, Object> execute(
      @Schema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @Schema(name = "plan_id", description = "Optional ID for the new plan", optional = true)
          String planId,
      @Schema(name = "name", description = "Name of the plan") String name,
      @Schema(name = "description", description = "What this plan accomplishes") String description,
      @Schema(name = "expected_outcome", description = "Expected result when the plan is complete")
          String expectedOutcome,
      @Schema(
              name = "subtasks",
              description =
                  "List of subtasks, each with 'name', 'description', 'expected_outcome', and optionally nested 'subtasks'")
          List<Map<String, Object>> subtasksList) {

    if (toolContext == null) {
      return Map.of("error", "toolContext is required");
    }

    final List<Plan> subtaskPlans = PlanningUtils.toPlans(subtasksList);
    final Plan currentPlan = new Plan(name, description, expectedOutcome, subtaskPlans);
    if (StringUtils.isNotBlank(planId)) {
      currentPlan.setId(planId);
    }
    PlanningUtils.savePlan(toolContext, currentPlan);

    LOG.info("Created plan '{}' with {} subtasks", currentPlan.getId(), subtaskPlans.size());
    return Map.of(
        "status", "success", "plan_id", currentPlan.getId(), "subtask_count", subtaskPlans.size());
  }
}
