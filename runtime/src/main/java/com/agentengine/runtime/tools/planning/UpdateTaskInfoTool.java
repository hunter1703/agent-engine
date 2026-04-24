package com.agentengine.runtime.tools.planning;

import com.agentengine.util.common.annotations.ToolSchema;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class UpdateTaskInfoTool extends UpdateTaskStatusTool {
    private static final String TOOL_NAME = "update_task_info";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Updates the descriptive metadata of an existing task: its name, goal, or extended description. Use "
                    + "when the scope or approach for a task becomes clearer during execution and the existing "
                    + "metadata no longer accurately describes what needs to be done. At least one of the optional "
                    + "fields must be provided; fields that are absent or blank are left unchanged. Does not modify "
                    + "task status or result — use dedicated status-transition tools for those. An active plan must "
                    + "exist and task_id must reference an existing task. "
                    + "Returns: { status: \"success\", task_id, new_status } or { error }. "
                    + "new_status reflects the task's current status, which is not modified by this call.",
            Map.of());

    public UpdateTaskInfoTool() {
        super(DESCRIPTOR);
    }

    public Map<String, Object> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(name = "task_id", description = "ID of the task whose metadata is to be updated.")
                    String taskId,
            @ToolSchema(
                            name = "name",
                            description = "Replacement short label for the task. Omit to leave unchanged.",
                            optional = true)
                    String name,
            @ToolSchema(
                            name = "goal",
                            description = "Replacement goal statement for the task. Omit to leave unchanged.",
                            optional = true)
                    String goal,
            @ToolSchema(
                            name = "description",
                            description = "Replacement extended description for the task. Omit to leave unchanged.",
                            optional = true)
                    String description) {

        return _execute(toolContext, taskId, name, goal, description, null, null);
    }
}
