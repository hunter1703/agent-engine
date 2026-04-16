package com.agentengine.runtime.tools.planning;

import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.planning.beans.Plan;
import com.agentengine.runtime.tools.planning.beans.Task;
import com.agentengine.runtime.tools.planning.beans.TaskStatus;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class CompleteTaskTool extends UpdateTaskStatusTool {
    private static final String TOOL_NAME = "complete_task";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Closes a task by transitioning it to a terminal status — either 'done' (completed successfully) or "
                    + "'abandoned' (intentionally skipped or cancelled). Call once the work for a task is done and "
                    + "you are ready to record the outcome and move on. A result describing the outcome is required. "
                    + "The task must not already be in a terminal state, and all of its child tasks must already be "
                    + "in a terminal state before the parent can be closed. After completion, the response includes "
                    + "the next pending task if one exists. "
                    + "Returns: { status, task_id, new_status, next_task } or { error }.",
            Map.of());

    public CompleteTaskTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(
                            name = "task_id",
                            description =
                                    "ID of the task to close. Must refer to a task in the current plan that has not "
                                            + "already been completed or abandoned.")
                    String taskId,
            @ToolSchema(
                            name = "status",
                            description =
                                    "Final disposition of the task. 'done' indicates successful completion; "
                                            + "'abandoned' indicates the task was intentionally skipped or is no longer applicable.",
                            enums = {"done", "abandoned"})
                    String status,
            @ToolSchema(
                            name = "result",
                            description =
                                    "Concise description of what was produced or decided. For 'done', summarise the "
                                            + "outcome. For 'abandoned', explain why the task was not completed.")
                    String result) {
        final TaskStatus newStatus = TaskStatus.valueOfOrDefault(status);
        if (newStatus == TaskStatus.UNKNOWN || !PlanningUtils.isTerminalStatus(newStatus)) {
            return Map.of(
                    "error",
                    "Only terminal statuses ("
                            + PlanningUtils.getTerminalStatuses(TaskStatus.class)
                            + ") are allowed. Found: "
                            + status);
        }
        final Map<String, Object> response =
                CollectionUtils.nullSafeMutableMap(_execute(toolContext, taskId, null, null, null, newStatus, result));
        final Plan currentPlan =
                RunUtils.getOrInitState(toolContext.invocationContext()).plan();
        final Task nextTask = PlanningUtils.findNextTodoTask(currentPlan);
        if (nextTask != null) {
            response.put(
                    "next_task", "Next recommended task: [" + nextTask.getTaskId() + "] (" + nextTask.getName() + ")");
        } else {
            response.put("next_task", "No more pending tasks. You may want to finish the plan.");
        }

        return response;
    }
}
