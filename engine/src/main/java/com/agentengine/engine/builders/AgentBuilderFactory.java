package com.agentengine.engine.builders;

import com.agentengine.engine.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentBuilderFactory {

  private final Map<String, AgentBuilder> agentNameVsBuilder;
  private final HybridAgentBuilder hybridAgentBuilder;

  @Inject
  public AgentBuilderFactory(final Instance<AgentBuilder> allBuilders, final HybridAgentBuilder hybridAgentBuilder) {
    agentNameVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), AgentBuilder::type,
        Function.identity());
    this.hybridAgentBuilder = hybridAgentBuilder;
  }

  public AgentBuilder getBuilder(final String type) {
    return agentNameVsBuilder.getOrDefault(type, hybridAgentBuilder);
  }
}
