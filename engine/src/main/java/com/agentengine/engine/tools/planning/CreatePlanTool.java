package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.annotations.ToolSchema;
import com.agentengine.engine.tools.planning.beans.Plan;
import com.agentengine.engine.tools.planning.beans.PlanStatus;
import com.agentengine.engine.tools.planning.beans.Task;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreatePlanTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(CreatePlanTool.class);
  private static final String TOOL_NAME = "create_plan";
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          TOOL_NAME,
          "Create a new plan with a list of tasks. Use 'parent_id' for logical hierarchy.",
          Map.of());

  public CreatePlanTool() {
    super(DESCRIPTOR);
  }

  public Map<String, Object> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(name = "title", description = "Short, descriptive title of the plan")
          String title,
      @ToolSchema(name = "goal", description = "Description of what this plan accomplishes")
          String goal,
      @ToolSchema(
              name = "tasks",
              description =
                  "Flat list of tasks, use 'parent_id' matching another task's 'id' for logical hierarchy")
          List<Task> tasks) {
    final RunState runState = RunStateUtils.getState(toolContext.invocationContext());
    final Plan existingPlan = runState.plan();
    if (existingPlan != null) {
      final PlanStatus status = existingPlan.getStatus();
      if (!status.isTerminal()) {
        return Map.of(
            "error",
            "Active plan already exists; finish it before creating a new plan.\n"
                + PlanningUtils.buildPlanSummary(existingPlan));
      }
    }
    final Plan currentPlan = new Plan(title, goal, tasks);

    final String validationError = currentPlan.validate();
    if (StringUtils.isNotBlank(validationError)) {
      return Map.of("error", validationError);
    }

    runState.updatePlan(currentPlan, toolContext);

    LOG.info("Created plan '{}' with {} tasks", currentPlan.getPlanId(), tasks.size());
    return Map.of(
        "status",
        "Success. Plan '"
            + title
            + "' has been created and saved with "
            + tasks.size()
            + " tasks. ");
  }
}
