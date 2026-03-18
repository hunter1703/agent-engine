package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.annotations.ToolSchema;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class UpdatePlanTool extends Tool {
  private static final String TOOL_NAME = "update_plan";
  public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(TOOL_NAME, "Update the general information of the current plan.",
      Map.of());

  public UpdatePlanTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true) ToolContext toolContext,
      @ToolSchema(name = "title", description = "New title for the plan (optional)", optional = true) String title,
      @ToolSchema(name = "goal", description = "New goal for the plan (optional)", optional = true) String goal) {
    final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
    final Plan currentPlan = runState.plan();
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }

    if (StringUtils.isNotBlank(title)) {
      currentPlan.setTitle(title);
    }
    if (StringUtils.isNotBlank(goal)) {
      currentPlan.setGoal(goal);
    }

    runState.updatePlan(currentPlan, toolContext);
    return Map.of("status", "success");
  }
}
