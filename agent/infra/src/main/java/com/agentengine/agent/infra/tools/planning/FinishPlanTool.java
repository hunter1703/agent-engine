package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class FinishPlanTool extends Tool {
  private static final String TOOL_NAME = "finish_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Closes the entire plan by setting it to a terminal state — either 'done' (goal achieved) or 'abandoned' "
              + "(work stopped before completion). Call once all tasks are in a terminal state and the work "
              + "the plan represents is complete or definitively stopped. A summary result is required. All "
              + "tasks must be in a terminal state (done or abandoned) before the plan can be finished — any "
              + "remaining TODO or IN_PROGRESS task will cause the call to fail. Once finished, no further "
              + "modifications to the plan or its tasks are permitted. "
              + "Returns: { status: \"success\", final_state } or { error }.",
          Map.of());

  public FinishPlanTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(
              name = "status",
              description =
                  "Final disposition of the plan. 'done' if the plan's goal was achieved; "
                      + "'abandoned' if the plan is being closed without completing its goal.",
              enums = {"done", "abandoned"})
          String status,
      @ToolSchema(
              name = "result",
              description =
                  "Summary of what was accomplished, or an explanation of why the plan was abandoned. Required.")
          String result) {
    final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
    final Plan currentPlan = runState.plan();
    if (currentPlan == null) {
      return ToolOutput.direct(Map.of("error", "No active plan found"));
    }

    final PlanStatus newStatus = PlanStatus.valueOfOrDefault(status);
    if (newStatus == PlanStatus.UNKNOWN) {
      return ToolOutput.direct(Map.of("error", "Invalid plan status: " + status));
    }
    final String validationError = currentPlan.canFinish(newStatus, result);
    if (validationError != null) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    currentPlan.finish(newStatus, result);

    runState.updatePlan(currentPlan, toolContext);
    return ToolOutput.direct(Map.of("status", "success", "final_state", status));
  }
}
