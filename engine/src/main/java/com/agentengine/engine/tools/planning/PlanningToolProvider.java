package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.FunctionTool;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public final class PlanningToolProvider implements ToolProvider, ToolSuite {
    private static final ToolDescriptor SUITE_DESCRIPTOR =
            new ToolDescriptor("planning", List.of(Tool.ALL), Map.of());
    private static final Map<String, Supplier<Tool>> TOOL_FACTORIES =
            Map.of(
                    CreatePlanTool.DESCRIPTOR.name(), CreatePlanTool::new,
                    UpdatePlanInfoTool.DESCRIPTOR.name(), UpdatePlanInfoTool::new,
                    RevisePlanTool.DESCRIPTOR.name(), RevisePlanTool::new,
                    UpdateTaskTool.DESCRIPTOR.name(), UpdateTaskTool::new,
                    CompletePlanTool.DESCRIPTOR.name(), CompletePlanTool::new,
                    ViewPlanTool.DESCRIPTOR.name(), ViewPlanTool::new);
    private static final List<ToolDescriptor> DESCRIPTORS = List.of(
            SUITE_DESCRIPTOR,
            CreatePlanTool.DESCRIPTOR,
            UpdatePlanInfoTool.DESCRIPTOR,
            RevisePlanTool.DESCRIPTOR,
            UpdateTaskTool.DESCRIPTOR,
            CompletePlanTool.DESCRIPTOR,
            ViewPlanTool.DESCRIPTOR);

    @Override
    public List<ToolDescriptor> tools() {
        return DESCRIPTORS;
    }

    @Override
    public List<String> toolNames() {
        return List.of(
                CreatePlanTool.DESCRIPTOR.name(),
                UpdatePlanInfoTool.DESCRIPTOR.name(),
                RevisePlanTool.DESCRIPTOR.name(),
                UpdateTaskTool.DESCRIPTOR.name(),
                CompletePlanTool.DESCRIPTOR.name(),
                ViewPlanTool.DESCRIPTOR.name());
    }

    @Override
    public BaseTool create(
            final AgentContext agentContext,
            final String toolName,
            final Map<String, Object> toolConfig) {
        if (StringUtils.isBlank(toolName)) {
            return null;
        }
        final Supplier<Tool> factory = TOOL_FACTORIES.get(toolName);
        if (factory == null) {
            return null;
        }
        return FunctionTool.create(factory.get(), "execute");
    }

    @Override
    public ToolDescriptor descriptor() {
        return SUITE_DESCRIPTOR;
    }
}
