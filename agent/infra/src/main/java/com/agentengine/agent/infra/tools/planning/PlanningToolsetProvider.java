package com.agentengine.agent.infra.tools.planning;

import com.agentengine.agent.infra.tools.AbstractToolsetProvider;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.validation.ValidationService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public final class PlanningToolsetProvider extends AbstractToolsetProvider {

  private static final ToolDescriptor TOOLSET_DESCRIPTOR =
      new ToolDescriptor("planning", "Tools for agent planning and task management.", Map.of());

  @Inject
  public PlanningToolsetProvider(
      final ValidationService validationService,
      final PlanningValidator planningValidator) {
    super(
        TOOLSET_DESCRIPTOR,
        List.of(
            new ToolDefinition(CreatePlanTool.DESCRIPTOR, () -> new CreatePlanTool(validationService)),
            new ToolDefinition(AddTaskTool.DESCRIPTOR, () -> new AddTaskTool(planningValidator)),
            new ToolDefinition(CompleteTaskTool.DESCRIPTOR, () -> new CompleteTaskTool(planningValidator))));
  }
}
