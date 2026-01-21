package com.agentengine.engine.api.builders;

public interface AgentBuilderFactory {

  AgentBuilder getBuilder(final String type);
}
