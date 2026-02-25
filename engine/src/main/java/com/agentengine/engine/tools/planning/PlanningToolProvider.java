package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.tools.Tool;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.tools.ToolProvider;
import com.agentengine.engine.api.tools.ToolSuite;
import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.tools.BaseTool;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public final class PlanningToolProvider implements ToolProvider, ToolSuite {
  private static final ToolDescriptor SUITE_DESCRIPTOR =
      new ToolDescriptor("planning", "Tools for agent planning and task management.", List.of(Tool.ALL), Map.of());
  private static final Map<String, Supplier<Tool>> TOOL_FACTORIES =
      Map.of(
          CreatePlanTool.DESCRIPTOR.name(), CreatePlanTool::new,
          UpdatePlanTool.DESCRIPTOR.name(), UpdatePlanTool::new,
          AddTaskTool.DESCRIPTOR.name(), AddTaskTool::new,
          UpdateTaskInfoTool.DESCRIPTOR.name(), UpdateTaskInfoTool::new,
          StartTaskTool.DESCRIPTOR.name(), StartTaskTool::new,
          CompleteTaskTool.DESCRIPTOR.name(), CompleteTaskTool::new,
          FinishPlanTool.DESCRIPTOR.name(), FinishPlanTool::new,
          ViewPlanTool.DESCRIPTOR.name(), ViewPlanTool::new);
  private static final List<ToolDescriptor> DESCRIPTORS =
      List.of(
          SUITE_DESCRIPTOR,
          CreatePlanTool.DESCRIPTOR,
          UpdatePlanTool.DESCRIPTOR,
          AddTaskTool.DESCRIPTOR,
          UpdateTaskInfoTool.DESCRIPTOR,
          StartTaskTool.DESCRIPTOR,
          CompleteTaskTool.DESCRIPTOR,
          FinishPlanTool.DESCRIPTOR,
          ViewPlanTool.DESCRIPTOR);

  @Override
  public List<ToolDescriptor> tools() {
    return DESCRIPTORS;
  }

  @Override
  public List<String> toolNames() {
    return new ArrayList<>(TOOL_FACTORIES.keySet());
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
    return factory.get();
  }

  @Override
  public ToolDescriptor descriptor() {
    return SUITE_DESCRIPTOR;
  }
}
