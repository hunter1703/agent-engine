package com.agentengine.runtime.tools.planning;

import com.agentengine.runtime.annotations.ToolSchema;
import com.agentengine.runtime.tools.Tool;
import com.agentengine.runtime.tools.planning.beans.Plan;
import com.agentengine.runtime.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class ViewPlanTool extends Tool {
    private static final String TOOL_NAME = "view_plan";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Retrieves the current plan in its entirety: title, goal, overall status, and all tasks with their "
                    + "individual statuses, results, and hierarchical relationships. Returns null if no plan exists "
                    + "for the current session. Use to inspect progress, determine which task to work on next, or "
                    + "confirm a task's current state before updating it. "
                    + "Returns: { planId, title, goal, status, result?, "
                    + "tasks: [{taskId, name, goal, description?, status, result?, parentId?}] }, or null.",
            Map.of());

    public ViewPlanTool() {
        super(DESCRIPTOR);
    }

    public Plan execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext) {
        return RunUtils.getOrInitState(toolContext.invocationContext()).plan();
    }
}
