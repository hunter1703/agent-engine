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
          progress reveals steps not captured in the original plan. An active plan must already exist. \
          Returns: { status: "success", task } on success, or { error } on failure.""",
          Map.of());

  public AddTaskTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolArg(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolArg(
              name = Constants.ToolArgs.PARENT,
              description = "Name of an existing parent task, if this is a subtask. Omit for top-level tasks.",
              optional = true)
          String parent,
      @ToolArg(
              name = "name",
              description = "Short label identifying this task. Must be unique within the plan.")
          String name,
      @ToolArg(
              name = "goal",
              description = "Concise statement of what this task should produce or achieve when completed.")
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

    String resolvedParent = parent;
    if (StringUtils.isNotBlank(resolvedParent)) {
      final Task existing = PlanningUtils.findTaskByName(currentPlan, resolvedParent);
      if (existing != null) {
        resolvedParent = existing.getName();
      }
    }

    final Task newTask = new Task(name, goal);
    if (StringUtils.isNotBlank(resolvedParent)) {
      newTask.setParent(resolvedParent);
    }
    if (StringUtils.isNotBlank(description)) {
      newTask.setDescription(description);
    }
    final String validationError = currentPlan.canAddTask(newTask);
    if (StringUtils.isNotBlank(validationError)) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    final Plan updatedPlan =
        applyAddTask(currentPlan, resolvedParent, name, goal, description);
    sessionState.updatePlan(updatedPlan);
    final Task addedTask = updatedPlan.getTasks().getLast();
    return ToolOutput.direct(
        Map.of(
            Constants.ToolStatus.STATUS,
            Constants.ToolStatus.SUCCESS,
            "task",
            addedTask.getName()));
  }

  public static Plan applyAddTask(
      final Plan plan,
      final String parent,
      final String name,
      final String goal,
      final String description) {
    final Task task = new Task(name, goal);
    if (StringUtils.isNotBlank(parent)) {
      task.setParent(parent);
    }
    if (StringUtils.isNotBlank(description)) {
      task.setDescription(description);
    }
    final List<Task> updatedTasks = CollectionUtils.nullSafeMutableList(plan.getTasks());
    updatedTasks.add(task);
    final Plan updatedPlan = new Plan(plan);
    updatedPlan.setTasks(updatedTasks);
    return updatedPlan;
  }
}
