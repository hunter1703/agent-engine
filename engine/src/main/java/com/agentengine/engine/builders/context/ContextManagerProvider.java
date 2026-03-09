package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.api.utils.AgentUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class ContextManagerProvider {

  private final Map<String, ContextManagerBuilder<?, ?>> typeVsBuilder;
  private final ContextManagerBuilder<?, ?> defaultBuilder;

  @Inject
  public ContextManagerProvider(
      final Instance<ContextManagerBuilder<?, ?>> allBuilders,
      final CompactionContextManagerBuilder compactionContextManagerBuilder) {
    this.typeVsBuilder =
        CollectionUtils.transformToMap(
            allBuilders.stream().toList(), ContextManagerBuilder::type, Function.identity());
    this.defaultBuilder = compactionContextManagerBuilder;
  }

  @SuppressWarnings("unchecked")
  public <C extends ContextStrategyConfig, CM extends ContextManager> ContextManager get(
      final BaseAgentConfig agentConfig) {
    final C resolvedConfig = (C) AgentUtils.resolveContextStrategy(agentConfig);
    // noinspection unchecked
    final ContextManagerBuilder<C, CM> builder =
        (ContextManagerBuilder<C, CM>)
            typeVsBuilder.getOrDefault(resolvedConfig.getType(), defaultBuilder);
    return builder.build(resolvedConfig, agentConfig);
  }
}
