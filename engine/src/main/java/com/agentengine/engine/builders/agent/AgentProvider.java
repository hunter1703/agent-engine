package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.agentengine.util.common.CollectionUtils;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentProvider {

  private final Map<String, AgentBuilder<?, ?>> typeVsBuilder;
  private final DefaultAgentBuilder defaultAgentBuilder;

  @Inject
  public AgentProvider(final @Any Instance<AgentBuilder<?, ?>> allBuilders) {
    typeVsBuilder =
        CollectionUtils.transformToMap(
            allBuilders.stream().toList(), AgentBuilder::type, Function.identity());
    this.defaultAgentBuilder = (DefaultAgentBuilder) typeVsBuilder.get("default");
  }

  @SuppressWarnings("unchecked")
  public <C extends BaseAgentConfig, A extends Agent> A get(final C config) {
    // noinspection unchecked
    final AgentBuilder<C, A> builder =
        (AgentBuilder<C, A>) typeVsBuilder.getOrDefault(config.getType(), defaultAgentBuilder);
    return builder.build(config);
  }
}
