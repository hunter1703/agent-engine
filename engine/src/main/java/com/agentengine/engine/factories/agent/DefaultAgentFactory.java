package com.agentengine.engine.factories.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.factories.model.ModelProvider;
import com.agentengine.engine.tools.ToolFactory;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("defaultAgentFactory")
public class DefaultAgentFactory extends AbstractAgentFactory<BaseAgentConfig, Agent> {

  @Inject
  public DefaultAgentFactory(final ModelProvider modelProvider, final ToolFactory toolFactory) {
    super(modelProvider, toolFactory);
  }

  @Override
  public DelegatedAgent build(final BaseAgentConfig config) {
    return createLlmAgentBuilder(config).build();
  }

  @Override
  public String type() {
    return BaseAgentConfig.AgentType.DEFAULT.type();
  }
}
