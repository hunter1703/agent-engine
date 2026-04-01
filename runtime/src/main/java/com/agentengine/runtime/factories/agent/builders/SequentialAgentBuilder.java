package com.agentengine.runtime.factories.agent.builders;

import com.agentengine.runtime.agents.Agent;
import com.agentengine.runtime.agents.DelegatedAgent;
import com.google.adk.agents.SequentialAgent;

public class SequentialAgentBuilder extends Agent.Builder<SequentialAgentBuilder, DelegatedAgent> {

    @Override
    public DelegatedAgent build() {
        final SequentialAgent sequentialAgent = SequentialAgent.builder()
                .name(name())
                .description(description())
                .subAgents(subAgents())
                .build();
        return new DelegatedAgent(sequentialAgent, agentConfig());
    }
}
