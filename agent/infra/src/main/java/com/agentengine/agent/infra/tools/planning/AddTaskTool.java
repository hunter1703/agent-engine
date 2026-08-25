package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.tools.beans.Task;
import com.agentengine.agent.infra.utils.SessionState;
import com.agentengine.agent.infra.utils.SessionUtils;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.List;
import java.util.Map;

public final class AddTaskTool extends Tool {
  public static final ToolDescriptor DESCRIPTOR =
      new ToolDescriptor(
          Constants.ADD_TASK_TOOL_NAME,
          "Appends a new task to the current active plan. Use when work in progress reveals steps not captured "
              + "in the original plan. The new task starts in pending (TODO) status. An active plan must "
              + "already exist. Optionally places the task under an existing parent task; the parent must not "
              + "already be in a terminal state (done or abandoned). Parent-child relationships affect "
              + "execution ordering: the parent must be IN_PROGRESS before the child can start, and the "
              + "parent cannot be completed until the child is terminal. "
              + "Returns: { status: \"success\", task_id } on success, or { error } on failure.",
          Map.of());

  public AddTaskTool() {
    super(DESCRIPTOR);
  }

  public ToolOutput<Map<String, Object>> execute(
      @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
          ToolContext toolContext,
      @ToolSchema(
              name = "parent_id",
              description =
                  "ID of an existing task in the current plan that this task is a child of. Affects "
                      + "execution ordering: the parent must be IN_PROGRESS before this task can be "
                      + "started, and the parent cannot be completed until this task reaches a terminal "
                      + "state. Omit for top-level tasks.",
              optional = true)
          String parentId,
      @ToolSchema(
              name = "name",
              description =
                  "Short label identifying this task. Should be unique within the plan for clarity.")
          String name,
      @ToolSchema(
              name = "goal",
              description =
                  "Concise statement of what this task is expected to produce or achieve when completed.")
          String goal,
      @ToolSchema(
              name = "description",
              description =
                  "Extended context, notes, or instructions for performing the task. Optional.",
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

    final Task newTask = new Task(name, goal);
    if (StringUtils.isNotBlank(parentId)) {
      newTask.setParentId(parentId);
    }
    final String validationError = currentPlan.canAddTask(newTask);
    if (StringUtils.isNotBlank(validationError)) {
      return ToolOutput.direct(Map.of("error", validationError));
    }

    final Plan updatedPlan = applyAddTask(currentPlan, parentId, name, goal, description);
    sessionState.updatePlan(updatedPlan);
    final Task addedTask = updatedPlan.getTasks().getLast();
    return ToolOutput.direct(Map.of("status", "success", "task_id", addedTask.getTaskId()));
  }

  public static Plan applyAddTask(
      final Plan plan,
      final String parentId,
      final String name,
      final String goal,
      final String description) {
    final Task task = new Task(name, goal);
    if (StringUtils.isNotBlank(parentId)) {
      task.setParentId(parentId);
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
