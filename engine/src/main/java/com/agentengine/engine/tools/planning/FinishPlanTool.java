package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

public final class FinishPlanTool extends Tool {
  private static final String TOOL_NAME = "finish_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME, "Mark the entire plan as finished with a final result.", Map.of());

  public FinishPlanTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(
              name = "status",
              description = "Final status of the plan",
              enums = {"done", "abandoned"})
          String status,
      @ToolSchema(name = "result", description = "The final result or summary of the plan")
          String result) {
    final RunState runState = RunStateUtils.getState(toolContext.invocationContext());
    final Plan currentPlan = runState.plan();
    if (currentPlan == null) {
      return Map.of("error", "No active plan found");
    }

    final PlanStatus newStatus = PlanStatus.valueOfOrDefault(status);
    if (newStatus == PlanStatus.UNKNOWN) {
      return Map.of("error", "Invalid plan status: " + status);
    }
    final String validationError = currentPlan.canFinish(newStatus, result);
    if (validationError != null) {
      return Map.of("error", validationError);
    }

    currentPlan.finish(newStatus, result);

    runState.updatePlan(currentPlan, toolContext);
    return Map.of("status", "success", "final_state", status);
  }
}
