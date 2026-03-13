package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.plugin.Agent;
import com.google.adk.agents.SequentialAgent;

public class SequentialAgentBuilder extends Agent.Builder<SequentialAgentBuilder, DelegatedAgent> {

  @Override
  public DelegatedAgent build() {
    final SequentialAgent sequentialAgent = SequentialAgent.builder().name(name()).description(description()).subAgents(subAgents())
        .build();
    return new DelegatedAgent(sequentialAgent, agentConfig());
  }
}
