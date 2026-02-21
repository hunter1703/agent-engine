package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.builders.ModelBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.repository.ModelRepository;
import com.google.adk.models.BaseLlm;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

@Singleton
public class ModelProvider {

  private final Map<String, ModelBuilder<? extends BaseLlm>> typeVsBuilder;
  private final ModelBuilder<?> defaultBuilder;
  private final ModelRepository modelRepository;

  @Inject
  public ModelProvider(
      final Instance<ModelBuilder<?>> allBuilders,
      final OpenAIModelBuilder openAIModelBuilder,
      ModelRepository modelRepository) {
    this.typeVsBuilder =
        CollectionUtils.transformToMap(
            allBuilders.stream().toList(), ModelBuilder::type, Function.identity());
    this.defaultBuilder = openAIModelBuilder;
    this.modelRepository = modelRepository;
  }

  public BaseLlm get(final AgentModelConfig config) {
    final ModelConfig modelConfig =
        Objects.requireNonNull(modelRepository.findById(config.getModelId()).orElse(null));
    final ModelBuilder<? extends BaseLlm> builder =
        typeVsBuilder.getOrDefault(modelConfig.getType().toLowerCase(), defaultBuilder);
    return builder.build(modelConfig);
  }
}
