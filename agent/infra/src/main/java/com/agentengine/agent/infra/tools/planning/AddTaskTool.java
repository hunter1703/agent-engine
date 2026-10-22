package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.agents.tools.ToolArg;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class AddTaskTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.ADD_TASK,
"""
          Adds a task to the active plan when the work turns up a step the plan lacks.
          Returns: { status: "success" }, or { error } on failure.""",
          Map.of());

  private final PlanningValidator planningValidator;

  public AddTaskTool(final PlanningValidator planningValidator) {
    super(DESCRIPTOR);
    this.planningValidator = planningValidator;
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.PARENT,
              description =
                  "Name of the task this one is a part of. Leave empty for a task that stands on its own.",
              optional = true)
          String parent,
      @ToolArg(
              name = "name",
              description = "Short label identifying this task. Must be unique within the plan.")
          String name,
      @ToolArg(
              name = "goal",
              description =
                  "Concise statement of what this task should produce or achieve when completed.")
          String goal,
      @ToolArg(
              name = "description",
              description = "Extended context, notes, or instructions for performing this task.",
              optional = true)
          String description) {
    final SessionState sessionState = SessionUtils.getSessionState(toolContext.invocationContext());
    final Plan currentPlan = sessionState.plan();
    if (currentPlan == null) {
      return ToolOutput.direct(Map.of("error", "No active plan found"));
    }

    if (StringUtils.isBlank(name)) {
      return ToolOutput.direct(Map.of("error", "Task name is required"));
    }
    if (StringUtils.isBlank(goal)) {
      return ToolOutput.direct(Map.of("error", "Task goal is required"));
    }

    final Task newTask = PlanningUtils.buildTask(currentPlan, name, parent, goal, description);
    final String validationError = planningValidator.canAddTask(currentPlan, newTask);
    if (StringUtils.isNotBlank(validationError)) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    final Plan updatedPlan = new Plan(currentPlan).addTask(newTask);
    sessionState.updatePlan(updatedPlan);
    return ToolOutput.direct(Map.of(Constants.ToolStatus.STATUS, Constants.ToolStatus.SUCCESS));
  }
}
