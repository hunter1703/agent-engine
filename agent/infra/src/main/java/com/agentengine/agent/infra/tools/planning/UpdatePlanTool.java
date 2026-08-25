package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class UpdatePlanTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.UPDATE_PLAN,
          "Revises the title and/or goal of the active plan. Use when the overall objective shifts direction or "
              + "the original framing needs correction. At least one of 'title' or 'goal' must be provided; "
              + "blank values are ignored. The plan must exist. "
              + "Returns: { status: \"success\" } or { error }.",
          Map.of());

  public UpdatePlanTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(
              name = "title",
              description =
                  "Replacement title for the plan. Omit or leave blank to retain the current title.",
              optional = true)
          String title,
      @ToolSchema(
              name = "goal",
              description =
                  "Replacement goal statement for the plan. Omit or leave blank to retain the current goal.",
              optional = true)
          String goal) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan currentPlan = sessionState.plan();
    if (currentPlan == null) {
      return ToolOutput.direct(Map.of("error", "No active plan found"));
    }

    final Plan updatedPlan = applyPlanUpdate(currentPlan, title, goal);
    sessionState.updatePlan(updatedPlan);
    return ToolOutput.direct(Map.of("status", "success"));
  }

  public static Plan applyPlanUpdate(final Plan plan, final String title, final String goal) {
    final Plan updatedPlan = new Plan(plan);
    if (StringUtils.isNotBlank(title)) {
      updatedPlan.setTitle(title);
    }
    if (StringUtils.isNotBlank(goal)) {
      updatedPlan.setGoal(goal);
    }
    return updatedPlan;
  }
}
