package com.agentengine.runtime.tools.orchestration;

import com.agentengine.runtime.actor.SessionActorFactory;
import com.agentengine.runtime.tools.AbstractToolsetProvider;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public final class AgentManagementToolsetProvider extends AbstractToolsetProvider {

  private static final ToolDescriptor TOOLSET_DESCRIPTOR = new ToolDescriptor("agent_tools",
      "Tools for spawning child agents, sending follow-up work, and awaiting their results.", Map.of());

  @Inject
  public AgentManagementToolsetProvider(final SessionActorFactory actorFactory) {
    super(TOOLSET_DESCRIPTOR,
        List.of(new ToolDefinition(SpawnAgentTool.DESCRIPTOR, () -> new SpawnAgentTool(actorFactory)),
            new ToolDefinition(SendMessageTool.DESCRIPTOR, () -> new SendMessageTool(actorFactory)),
            new ToolDefinition(AwaitAgentTool.DESCRIPTOR, () -> new AwaitAgentTool(actorFactory))));
  }
}
