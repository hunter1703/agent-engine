package com.agentengine.runtime.tools.planning;

import com.agentengine.runtime.tools.AbstractToolsetProvider;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public final class PlanningToolsetProvider extends AbstractToolsetProvider {

    private static final ToolDescriptor TOOLSET_DESCRIPTOR =
            new ToolDescriptor("planning", "Tools for agent planning and task management.", Map.of());

    public PlanningToolsetProvider() {
        super(
                TOOLSET_DESCRIPTOR,
                List.of(
                        new ToolDefinition(CreatePlanTool.DESCRIPTOR, CreatePlanTool::new),
                        new ToolDefinition(UpdatePlanTool.DESCRIPTOR, UpdatePlanTool::new),
                        new ToolDefinition(AddTaskTool.DESCRIPTOR, AddTaskTool::new),
                        new ToolDefinition(UpdateTaskInfoTool.DESCRIPTOR, UpdateTaskInfoTool::new),
                        new ToolDefinition(StartTaskTool.DESCRIPTOR, StartTaskTool::new),
                        new ToolDefinition(CompleteTaskTool.DESCRIPTOR, CompleteTaskTool::new),
                        new ToolDefinition(FinishPlanTool.DESCRIPTOR, FinishPlanTool::new),
                        new ToolDefinition(ViewPlanTool.DESCRIPTOR, ViewPlanTool::new)));
    }
}
