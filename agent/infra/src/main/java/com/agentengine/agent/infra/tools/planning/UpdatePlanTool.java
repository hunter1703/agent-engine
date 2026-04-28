package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.Tool;
import com.agentengine.agent.infra.tools.beans.Plan;
import com.agentengine.agent.infra.utils.RunState;
import com.agentengine.agent.infra.utils.RunUtils;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.agents.beans.tools.ToolOutput;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.annotations.ToolSchema;
import com.google.adk.tools.ToolContext;
import java.util.Map;

public final class UpdatePlanTool extends Tool {
    private static final String TOOL_NAME = "update_plan";
    public static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_NAME,
            "Revises the title and/or goal of the active plan. Use when the overall objective shifts direction or "
                    + "the original framing needs correction. At least one of 'title' or 'goal' must be provided; "
                    + "blank values are ignored. The plan must exist. "
                    + "Returns: { status: \"success\" } or { error }.",
            Map.of());

    public UpdatePlanTool() {
        super(DESCRIPTOR);
    }

    public ToolOutput<Map<String, Object>> execute(
            @ToolSchema(name = "toolContext", description = "Injected runtime context", optional = true)
                    ToolContext toolContext,
            @ToolSchema(
                            name = "title",
                            description =
                                    "Replacement title for the plan. Omit or leave blank to retain the current title.",
                            optional = true)
                    String title,
            @ToolSchema(
                            name = "goal",
                            description =
                                    "Replacement goal statement for the plan. Omit or leave blank to retain the current goal.",
                            optional = true)
                    String goal) {
        final RunState runState = RunUtils.getOrInitState(toolContext.invocationContext());
        final Plan currentPlan = runState.plan();
        if (currentPlan == null) {
            return ToolOutput.direct(Map.of("error", "No active plan found"));
        }

        if (StringUtils.isNotBlank(title)) {
            currentPlan.setTitle(title);
        }
        if (StringUtils.isNotBlank(goal)) {
            currentPlan.setGoal(goal);
        }

        runState.updatePlan(currentPlan, toolContext);
        return ToolOutput.direct(Map.of("status", "success"));
    }
}
