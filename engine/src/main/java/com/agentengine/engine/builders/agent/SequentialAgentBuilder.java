package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.agents.DelegatedAgent;
import com.google.adk.agents.SequentialAgent;

public class SequentialAgentBuilder extends Agent.Builder<SequentialAgentBuilder, DelegatedAgent> {

  @Override
  public DelegatedAgent build() {
    final SequentialAgent sequentialAgent = SequentialAgent.builder().name(name()).description(description()).subAgents(subAgents()).build();
    return new DelegatedAgent(sequentialAgent, agentConfig());
  }
}
