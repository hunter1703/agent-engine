package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.api.beans.config.SummarizeContextManagerConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
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
      final SummarizeContextManagerBuilder summarizeContextManagerBuilder) {
    this.typeVsBuilder =
        CollectionUtils.transformToMap(
            allBuilders.stream().toList(), ContextManagerBuilder::type, Function.identity());
    this.defaultBuilder = summarizeContextManagerBuilder;
  }

  @SuppressWarnings("unchecked")
  public <C extends ContextManagerConfig, CM extends ContextManager> ContextManager get(
      final C config, final AgentConfig agentConfig) {
    final C resolvedConfig =
        config == null ? (C) new SummarizeContextManagerConfig() : config;
    // noinspection unchecked
    final ContextManagerBuilder<C, CM> builder =
        (ContextManagerBuilder<C, CM>)
            typeVsBuilder.getOrDefault(resolvedConfig.getType(), defaultBuilder);
    return builder.build(resolvedConfig, agentConfig);
  }
}
