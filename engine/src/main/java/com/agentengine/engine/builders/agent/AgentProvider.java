package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.builders.AgentBuilder;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentProvider {

  private final Map<String, AgentBuilder<?, ?>> typeVsBuilder;
  private final HybridAgentBuilder hybridAgentBuilder;

  @Inject
  public AgentProvider(final Instance<AgentBuilder<?, ?>> allBuilders,
                       final HybridAgentBuilder hybridAgentBuilder) {
    typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), AgentBuilder::type,
        Function.identity());
    this.hybridAgentBuilder = hybridAgentBuilder;
  }

  public <C extends AgentConfig, A extends Agent> A get(final C config) {
    //noinspection unchecked
    final AgentBuilder<C, A> builder = (AgentBuilder<C, A>) typeVsBuilder.getOrDefault(config.getType(), hybridAgentBuilder);
    return builder.build(config);
  }
}
