package com.agentengine.engine.tools.planning;

import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.plugin.tools.Tool;
import com.agentengine.engine.plugin.tools.ToolsetProvider;
import com.google.adk.agents.ReadonlyContext;
import com.google.adk.tools.BaseTool;
import com.google.adk.tools.BaseToolset;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

@Singleton
public final class PlanningToolsetProvider implements ToolsetProvider {

  private static final ToolDescriptor TOOLSET_DESCRIPTOR = new ToolDescriptor("planning", "Tools for agent planning and task management.",
      Map.of());
  private static final List<ToolDefinition> TOOL_DEFINITIONS = List.of(new ToolDefinition(CreatePlanTool.DESCRIPTOR, CreatePlanTool::new),
      new ToolDefinition(UpdatePlanTool.DESCRIPTOR, UpdatePlanTool::new), new ToolDefinition(AddTaskTool.DESCRIPTOR, AddTaskTool::new),
      new ToolDefinition(UpdateTaskInfoTool.DESCRIPTOR, UpdateTaskInfoTool::new),
      new ToolDefinition(StartTaskTool.DESCRIPTOR, StartTaskTool::new),
      new ToolDefinition(CompleteTaskTool.DESCRIPTOR, CompleteTaskTool::new),
      new ToolDefinition(FinishPlanTool.DESCRIPTOR, FinishPlanTool::new), new ToolDefinition(ViewPlanTool.DESCRIPTOR, ViewPlanTool::new));
  private static final List<ToolDescriptor> TOOL_DESCRIPTORS = TOOL_DEFINITIONS.stream().map(ToolDefinition::descriptor).toList();

  @Override
  public ToolDescriptor descriptor() {
    return TOOLSET_DESCRIPTOR;
  }

  @Override
  public List<ToolDescriptor> memberDescriptors() {
    return TOOL_DESCRIPTORS;
  }

  @Override
  public BaseToolset create(final Map<String, Object> toolConfig) {
    return new PlanningToolset();
  }

  private record ToolDefinition(ToolDescriptor descriptor, Supplier<Tool> factory) {
  }

  private static final class PlanningToolset implements BaseToolset {
    @Override
    public Flowable<BaseTool> getTools(final ReadonlyContext context) {
      return Flowable.fromIterable(TOOL_DEFINITIONS).map(definition -> definition.factory().get());
    }

    @Override
    public void close() {
      // No resources to release.
    }
  }
}
