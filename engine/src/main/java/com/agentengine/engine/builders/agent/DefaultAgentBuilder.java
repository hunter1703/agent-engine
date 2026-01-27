package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import com.agentengine.engine.tools.ToolRegistry;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public final class DefaultAgentBuilder extends AbstractAgentBuilder<AgentConfig, DefaultAgent> {

  @Inject
  public DefaultAgentBuilder(ModelProvider modelProvider, SessionStoreProvider sessionStoreProvider,
      ToolRegistry toolRegistry) {
    super(modelProvider, sessionStoreProvider, toolRegistry);
  }

  @Override
  public DefaultAgent build(AgentConfig config) {
    final LLMModel model = modelProvider.get(config.getAgentId(), config.getModel(), null);
    return new DefaultAgent(config.getAgentId(), "Default Agent - single model agent", model,
        getDefaultToolExecutor(config.getAgentId(), config.getModel().getTools()));
  }

  @Override
  public String type() {
    return AgentConfig.AgentType.DEFAULT.name().toLowerCase();
  }
}