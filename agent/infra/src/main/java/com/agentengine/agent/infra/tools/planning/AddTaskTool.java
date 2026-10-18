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
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

public final class AddTaskTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ToolNames.ADD_TASK,
"""
           Appends a new task to the current active plan in pending (TODO) status. Use when work in \
           progress reveals tasks not captured in the original plan or scope of the problem changes. An active plan must already exist. \
           Only add tasks that are genuinely needed — do not add tasks that duplicate or overlap with \
           existing ones. Returns: { status: "success", task } on success, or { error } on failure.""",
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
                  """
                  Name of an existing parent task ONLY if this task is an internal sub-part of it. \
                  DO NOT use 'parent' for sequential phases, execution order, or dependencies. Omit for top-level tasks.""",
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

    // Resolve parent name to its canonical casing as stored in the plan.
    final String resolvedParent;
    if (StringUtils.isNotBlank(parent)) {
      final Task existing = PlanningUtils.findTaskByName(currentPlan, parent);
      resolvedParent = existing != null ? existing.getName() : parent;
    } else {
      resolvedParent = null;
    }

    final Task newTask = buildTask(name, resolvedParent, goal, description);
    final String validationError = planningValidator.canAddTask(currentPlan, newTask);
    if (StringUtils.isNotBlank(validationError)) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    final Plan updatedPlan = applyAddTask(currentPlan, newTask);
    sessionState.updatePlan(updatedPlan);
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.SUCCESS,
            "task",
            newTask.getName()));
  }

  public static Plan applyAddTask(final Plan plan, final String parent, final String name,
      final String goal, final String description) {
    return applyAddTask(plan, buildTask(name, parent, goal, description));
  }

  public static Plan applyAddTask(final Plan plan, final Task task) {
    final Plan updatedPlan = new Plan(plan);
    updatedPlan.addTask(task);
    return updatedPlan;
  }

  private static Task buildTask(
      final String name, final String parent, final String goal, final String description) {
    final Task task = new Task(name, goal);
    if (StringUtils.isNotBlank(parent)) {
      task.setParent(parent);
    }
    if (StringUtils.isNotBlank(description)) {
      task.setDescription(description);
    }
    return task;
  }
}
