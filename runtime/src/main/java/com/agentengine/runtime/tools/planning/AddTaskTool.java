package com.agentengine.runtime.tools.planning;

import com.agentengine.runtime.annotations.ToolSchema;
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
            TOOL_NAME, "Add a new task to the current plan, optionally under a parent task.", Map.of());

    public AddTaskTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(
                            name = "parent_id",
                            description = "Optional parent task id to attach this task under",
                            optional = true)
                    String parentId,
            @ToolSchema(name = "name", description = "Short name for the new task") String name,
            @ToolSchema(name = "goal", description = "The goal or expected result of this task") String goal,
            @ToolSchema(name = "description", description = "Detailed description of the task", optional = true)
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
