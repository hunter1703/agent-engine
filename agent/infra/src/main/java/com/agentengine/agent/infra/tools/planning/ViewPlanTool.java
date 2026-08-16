package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class ViewPlanTool extends Tool {
  private static final String TOOL_NAME = "view_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Retrieves the current plan in its entirety: title, goal, overall status, and all tasks with their "
              + "individual statuses, results, and hierarchical relationships. Returns null if no plan exists "
              + "for the current session. Use to inspect progress, determine which task to work on next, or "
              + "confirm a task's current state before updating it. "
              + "Returns: { planId, title, goal, status, result?, "
              + "tasks: [{taskId, name, goal, description?, status, result?, parentId?}] }, or null.",
          Map.of());

  public ViewPlanTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Plan> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext) {
    return ToolOutput.direct(RunUtils.getOrInitState(toolContext.invocationContext()).plan());
  }
}
