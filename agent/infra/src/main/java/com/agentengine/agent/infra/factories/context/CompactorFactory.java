package com.agentengine.agent.infra.factories.context;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.google.adk.summarizer.EventCompactor;

public interface CompactorFactory<C extends ContextStrategyConfig> {

  EventCompactor build(C contextConfig, BaseAgentConfig agentConfig);

  String type();
}
