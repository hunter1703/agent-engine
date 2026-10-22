package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class AbandonPlanTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.ABANDON_PLAN,
          """
          Abandons the active plan and every task of it still open, so a new plan can replace it. \
          Use it when the work must change direction. A plan that is carried out completes by \
          itself once all its tasks are done.
          Returns: { status: "success" }, or { error } on failure.""",
          Map.of());

  public AbandonPlanTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          final ToolContext toolContext,
      @ToolArg(name = Constants.ToolArgs.REASON, description = "Why the plan is abandoned.")
          final String reason) {
    if (StringUtils.isBlank(reason)) {
      return ToolOutput.direct(Map.of("error", "A reason is required to abandon the plan."));
    }
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan currentPlan = sessionState.plan();
    if (!sessionState.hasActivePlan()) {
      return ToolOutput.direct(Map.of("error", "No active plan found"));
    }
    sessionState.updatePlan(new Plan(currentPlan).abandon(reason));
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
