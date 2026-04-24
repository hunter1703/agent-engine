package com.agentengine.runtime.tools.planning;

import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.runtime.tools.planning.beans.TaskStatus;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class StartTaskTool extends UpdateTaskStatusTool {
    private static final String TOOL_NAME = "start_task";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Transitions a task's status from pending (TODO) to active (IN_PROGRESS), signalling that work has begun. "
                    + "Three preconditions are enforced: (1) an active plan must exist; (2) all ancestor tasks of the "
                    + "target task must already be IN_PROGRESS; (3) the target task must be the next task in "
                    + "depth-first plan order — skipping ahead to a later task is rejected. Call this immediately "
                    + "before beginning execution of the task. "
                    + "Returns: { status: \"success\", task_id, new_status } or { error }.",
            Map.of());

    public StartTaskTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(
                            name = "task_id",
                            description =
                                    "ID of the task to mark as in-progress. Must be an existing task in the current plan "
                                            + "that is currently in pending (TODO) status.")
                    String taskId) {
        return _execute(toolContext, taskId, null, null, null, TaskStatus.IN_PROGRESS, null);
    }
}
