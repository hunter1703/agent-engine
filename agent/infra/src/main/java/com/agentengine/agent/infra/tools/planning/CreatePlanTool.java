package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
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
          "Initialises a new structured plan with a title, goal, and initial list of tasks. Use at the start of "
              + "any multi-step task where tracking completion state across distinct phases has value — if the "
              + "work can be done in a single step without meaningful state to track, a plan is not needed. "
              + "A plan is a persistent work-tracking structure that records what needs to be done and tracks "
              + "completion. Only one active (non-finished) plan may exist at a time — calling this while a "
              + "non-terminal plan is active will fail. Tasks are provided as a flat list; parent_id "
              + "establishes both hierarchy and execution ordering (parent must be started before children; "
              + "children must be terminal before parent can complete). "
              + "Returns: { status, createdPlan } on success, where createdPlan contains the full plan "
              + "including the assigned task_id for every task — these IDs are required for all subsequent "
              + "task operations; or { error } on failure.",
          Map.of());

  public CreatePlanTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(
              name = "title",
              description = "Short human-readable label that identifies the plan.")
          String title,
      @ToolSchema(
              name = "goal",
              description =
                  "Clear description of the objective this plan is intended to achieve. Guides task "
                      + "prioritisation and completion criteria.")
          String goal,
      @ToolSchema(
              name = "tasks",
              description =
                  "Initial list of task objects. Each task requires 'name' (short label) and 'goal' "
                      + "(expected result). Optionally include 'description' for detail. To establish "
                      + "parent-child relationships within this list, explicitly set 'task_id' on the parent "
                      + "task and reference that same value in the child's 'parent_id' — IDs are otherwise "
                      + "assigned automatically and not predictable. May be empty.")
          List<Task> tasks) {
    final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
    final Plan existingPlan = runState.plan();
    if (existingPlan != null) {
      final PlanStatus status = existingPlan.getStatus();
      if (!status.isTerminal()) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "Active plan already exists; finish it before creating a new plan.\n"
                    + PlanningUtils.buildPlanSummary(existingPlan)));
      }
    }
    final Plan currentPlan = new Plan(title, goal, tasks);

    final String validationError = currentPlan.validate();
    if (StringUtils.isNotBlank(validationError)) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    runState.updatePlan(currentPlan, toolContext);

    LOG.info("Created plan '{}' with {} tasks", currentPlan.getPlanId(), tasks.size());
    return ToolOutput.direct(
        Map.of(
            "status",
            "Success. Plan '"
                + title
                + "' has been created and saved with "
                + tasks.size()
                + " tasks. ",
            "createdPlan",
            currentPlan));
  }
}
