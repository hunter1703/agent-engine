package com.agentengine.engine.client.builders;

public interface AgentBuilderFactory {

  AgentBuilder getBuilder(final String type);
}
