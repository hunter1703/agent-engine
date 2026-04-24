package com.agentengine.runtime.tools.planning;

import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.tools.planning.beans.Plan;
import com.agentengine.runtime.tools.planning.beans.Task;
import com.agentengine.runtime.utils.RunState;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.StringUtils;
import com.google.adk.tools.ToolContext;
import java.util.ArrayList;
import java.util.Map;

public final class AddTaskTool extends Tool {
    private static final String TOOL_NAME = "add_task";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
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

    public Map<String, Object> execute(
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
                            description = "Extended context, notes, or instructions for performing the task. Optional.",
                            optional = true)
                    String description) {
        final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
        final Plan currentPlan = runState.plan();
        if (currentPlan == null) {
            return Map.of("error", "No active plan found");
        }

        if (StringUtils.isBlank(name)) {
            return Map.of("error", "Task name is required");
        }
        if (StringUtils.isBlank(goal)) {
            return Map.of("error", "Task goal is required");
        }

        final Task task = new Task(name, goal);
        if (StringUtils.isNotBlank(parentId)) {
            task.setParentId(parentId);
        }
        if (StringUtils.isNotBlank(description)) {
            task.setDescription(description);
        }

        final String validationError = currentPlan.canAddTask(task);
        if (StringUtils.isNotBlank(validationError)) {
            return Map.of("error", validationError);
        }

        if (currentPlan.getTasks() == null) {
            currentPlan.setTasks(new ArrayList<>());
        }
        currentPlan.getTasks().add(task);

        runState.updatePlan(currentPlan, toolContext);
        return Map.of("status", "success", "task_id", task.getTaskId());
    }
}
