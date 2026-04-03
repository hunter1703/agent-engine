package com.agentengine.runtime.tools.agent;

import com.agentengine.runtime.tools.AbstractToolsetProvider;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.pekko.ActorSystemProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
public final class AgentManagementToolsetProvider extends AbstractToolsetProvider {

    private static final ToolDescriptor TOOLSET_DESCRIPTOR = new ToolDescriptor(
            "agent_tools",
            "Tools for spawning child agents, sending follow-up work, and awaiting their results.",
            Map.of());

    @Inject
    public AgentManagementToolsetProvider(final ActorSystemProvider actorSystemProvider) {
        super(
                TOOLSET_DESCRIPTOR,
                List.of(
                        new ToolDefinition(SpawnAgentTool.DESCRIPTOR, () -> new SpawnAgentTool(actorSystemProvider)),
                        new ToolDefinition(SendMessageTool.DESCRIPTOR, () -> new SendMessageTool(actorSystemProvider)),
                        new ToolDefinition(AwaitAgentTool.DESCRIPTOR, () -> new AwaitAgentTool(actorSystemProvider))));
    }
}
