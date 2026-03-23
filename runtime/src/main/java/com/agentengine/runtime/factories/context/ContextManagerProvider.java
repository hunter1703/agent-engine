package com.agentengine.runtime.factories.context;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.runtime.context.ContextManager;
import com.agentengine.runtime.factories.context.ContextManagerFactory;
import com.agentengine.runtime.utils.AgentUtils;
import com.agentengine.util.common.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class ContextManagerProvider {

  private final Map<String, ContextManagerFactory<?, ?>> typeVsFactory;
  private final ContextManagerFactory<?, ?> defaultFactory;

  @Inject
  public ContextManagerProvider(final Instance<ContextManagerFactory<?, ?>> allFactories,
      final CompactionContextManagerFactory compactionContextManagerFactory) {
    this.typeVsFactory = CollectionUtils.transformToMap(allFactories.stream().toList(), ContextManagerFactory::type, Function.identity());
    this.defaultFactory = compactionContextManagerFactory;
  }

  @SuppressWarnings("unchecked")
  public <C extends ContextStrategyConfig, CM extends ContextManager> ContextManager create(final BaseAgentConfig agentConfig) {
    final C resolvedConfig = (C) AgentUtils.resolveContextStrategy(agentConfig);
    // noinspection unchecked
    final ContextManagerFactory<C, CM> factory = (ContextManagerFactory<C, CM>) typeVsFactory.getOrDefault(resolvedConfig.getType(),
        defaultFactory);
    return factory.build(resolvedConfig, agentConfig);
  }
}
