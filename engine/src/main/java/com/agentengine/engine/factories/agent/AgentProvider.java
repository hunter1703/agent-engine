package com.agentengine.engine.factories.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.factories.AgentFactory;
import com.agentengine.util.common.CollectionUtils;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentProvider {

  private final Map<String, AgentFactory<?, ?>> typeVsFactory;
  private final DefaultAgentFactory defaultAgentFactory;

  @Inject
  public AgentProvider(final @Any Instance<AgentFactory<?, ?>> allFactories) {
    typeVsFactory =
        CollectionUtils.transformToMap(
            allFactories.stream().toList(), AgentFactory::type, Function.identity());
    this.defaultAgentFactory =
        (DefaultAgentFactory) typeVsFactory.get(BaseAgentConfig.AgentType.DEFAULT.name());
  }

  @SuppressWarnings("unchecked")
  public <C extends BaseAgentConfig, A extends Agent> A create(final C config) {
    // noinspection unchecked
    final AgentFactory<C, A> factory =
        (AgentFactory<C, A>) typeVsFactory.getOrDefault(config.getType(), defaultAgentFactory);
    return factory.build(config);
  }
}
