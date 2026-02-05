package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.builders.ModelBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.models.BaseLlm;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class ModelProvider {

  private final Map<String, ModelBuilder<? extends BaseLlm>> typeVsBuilder;
  private final ModelBuilder<?> defaultBuilder;

  @Inject
  public ModelProvider(final Instance<ModelBuilder<?>> allBuilders, final LangchainModelBuilder langchainModelBuilder) {
    this.typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), ModelBuilder::type,
        Function.identity());
    this.defaultBuilder = langchainModelBuilder;
  }

  public <C extends AgentModelConfig> BaseLlm get(final String agentId, final C config) {
    final ModelBuilder<? extends BaseLlm> builder = typeVsBuilder.getOrDefault(config.getType(),
        defaultBuilder);
    return builder.build(agentId, config);
  }
}
