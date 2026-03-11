package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DelegatedAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.services.SessionService;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.repository.ModelRepository;
import com.agentengine.engine.tools.ToolRegistry;
import io.quarkus.arc.WithCaching;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("defaultAgentBuilder")
public class DefaultAgentBuilder extends AbstractAgentBuilder<BaseAgentConfig, Agent> {

  @Inject
  public DefaultAgentBuilder(
      ModelProvider modelProvider,
      SessionService sessionService,
      ToolRegistry toolRegistry,
      ModelRepository modelRepository,
      @WithCaching Instance<AgentProvider> agentProvider) {
    super(modelProvider, sessionService, toolRegistry, modelRepository, agentProvider);
  }

  @Override
  public DelegatedAgent build(final BaseAgentConfig config) {
    return getBuilder(config).build();
  }

  @Override
  public String type() {
    return BaseAgentConfig.AgentType.DEFAULT.name().toLowerCase();
  }
}
