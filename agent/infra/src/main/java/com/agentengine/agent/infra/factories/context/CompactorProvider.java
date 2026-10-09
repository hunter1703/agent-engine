package com.agentengine.agent.infra.factories.context;

import com.agentengine.agent.infra.utils.AgentUtils;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.common.utils.CollectionUtils;
import com.google.adk.summarizer.EventCompactor;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

/** Builds the {@link EventCompactor} an agent's context strategy configures. */
@Singleton
public class CompactorProvider {

  private final Map<String, CompactorFactory<?>> typeVsFactory;
  private final CompactorFactory<?> defaultFactory;

  @Inject
  public CompactorProvider(
      final Instance<CompactorFactory<?>> allFactories,
      final LLMCompactorFactory llmCompactorFactory) {
    this.typeVsFactory =
        CollectionUtils.transformToMap(
            allFactories.stream().toList(), CompactorFactory::type, Function.identity());
    this.defaultFactory = llmCompactorFactory;
  }

  @SuppressWarnings("unchecked")
  public <C extends ContextStrategyConfig> EventCompactor create(final BaseAgentConfig agentConfig) {
    final C config = (C) AgentUtils.resolveContextStrategy(agentConfig);
    final CompactorFactory<C> factory =
        (CompactorFactory<C>) typeVsFactory.getOrDefault(config.getType(), defaultFactory);
    return factory.build(config, agentConfig);
  }
}
