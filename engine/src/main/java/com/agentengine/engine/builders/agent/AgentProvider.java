package com.agentengine.engine.builders.agent;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.AgentContext;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.builders.AgentBuilder;
import com.google.adk.agents.LlmAgent;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentProvider {

  private final Map<String, AgentBuilder<?, ?>> typeVsBuilder;
  private final SimpleAgentBuilder defaultAgentBuilder;

  @Inject
  public AgentProvider(final Instance<AgentBuilder<?, ?>> allBuilders,
      @Named("simpleAgentBuilder") final SimpleAgentBuilder simpleAgentBuilder) {
    typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), AgentBuilder::type,
        Function.identity());
    this.defaultAgentBuilder = simpleAgentBuilder;
  }

  public <C extends AgentConfig, A extends LlmAgent> A get(final C config, final AgentContext agentContext) {
    // noinspection unchecked
    final AgentBuilder<C, A> builder = (AgentBuilder<C, A>) typeVsBuilder.getOrDefault(config.getType(),
        defaultAgentBuilder);
    return builder.build(config, agentContext);
  }
}
