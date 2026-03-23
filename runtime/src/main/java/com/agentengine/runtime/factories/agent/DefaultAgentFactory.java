package com.agentengine.runtime.factories.agent;

import com.agentengine.runtime.agents.DelegatedAgent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.runtime.agents.Agent;
import com.agentengine.runtime.tools.ToolFactory;
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
