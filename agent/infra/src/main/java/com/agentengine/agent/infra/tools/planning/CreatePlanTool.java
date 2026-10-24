package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.PlanStatus;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.tools.beans.TaskStatus;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CreatePlanTool extends Tool {
  private static final Logger LOG = LoggerFactory.getLogger(CreatePlanTool.class);
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.CREATE_PLAN,
"""
          Creates the plan for work of several distinct steps. Skip it for a single step or a \
          conversational reply. Only one plan can be active at a time. The first task, with the tasks it is part of, starts in progress, \
          and completing a task starts the next. Work on one task at a time, and give the final \
          answer only once every task is completed or abandoned.
          Returns: { active_task } naming the task now in progress, or { error } on failure.""",
          Map.of());

  private final PlanningValidator planningValidator;

  public CreatePlanTool(final PlanningValidator planningValidator) {
    super(DESCRIPTOR);
    this.planningValidator = planningValidator;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolArg(name = "title", description = "Short human-readable label that identifies the plan.")
          String title,
      @ToolArg(
          name = "goal",
          description =
              """
              Clear description of the objective this plan is intended to achieve. Guides task \
              prioritisation and completion criteria.""")
          String goal,
      @ToolArg(name = "tasks", description = "Initial list of tasks comprising the plan.")
          List<Task> tasks) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan existingPlan = sessionState.plan();
    if (existingPlan != null) {
      final PlanStatus status = existingPlan.getStatus();
      if (!status.isTerminal()) {
        return ToolOutput.direct(
            Map.of(
                "error",
                "Active plan '"
                    + existingPlan.getTitle()
                    + "' already exists — see the Active Plan reminder above for its current "
                    + "state. Complete or abandon the active plan before creating a new one."));
      }
    }

    final List<Task> planTasks = PlanningUtils.normalizedTasks(tasks);
    final List<String> validationErrors = planningValidator.errors(title, goal, planTasks);
    if (!validationErrors.isEmpty()) {
      return ToolOutput.direct(Map.of("error", String.join("; ", validationErrors)));
    }
    final Plan currentPlan = PlanningUtils.buildPlan(title, goal, planTasks);

    sessionState.updatePlan(currentPlan);
    LOG.debug("Created plan '{}' with {} tasks", currentPlan.getTitle(), currentPlan.getTasks().size());
    return ToolOutput.direct(
        Map.of("active_task", currentPlan.getNextTaskWithStatus(TaskStatus.IN_PROGRESS).getName()));
  }

  @Override
  protected boolean citesTask() {
    return false;
  }
}
