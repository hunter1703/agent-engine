package com.agentengine.engine.builders.model;

import com.agentengine.engine.api.ConfigRepository;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.builders.ModelBuilder;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.utils.Parser;
import com.google.adk.models.BaseLlm;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class ModelProvider {

  private final Map<String, ModelBuilder<? extends BaseLlm>> typeVsBuilder;
  private final ModelBuilder<?> defaultBuilder;
  private final ConfigRepository configRepository;

  @Inject
  public ModelProvider(final Instance<ModelBuilder<?>> allBuilders, final OpenAIModelBuilder openAIModelBuilder, ConfigRepository configRepository) {
    this.typeVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), ModelBuilder::type,
        Function.identity());
    this.defaultBuilder = openAIModelBuilder;
      this.configRepository = configRepository;
  }

  public BaseLlm get(final String agentId, final AgentModelConfig config) {
    final ModelConfig modelConfig = configRepository.loadModelConfig(config.getModelId());
    final ModelBuilder<? extends BaseLlm> builder = typeVsBuilder.getOrDefault(modelConfig.getType().toLowerCase(),
        defaultBuilder);
    return builder.build(agentId, config, modelConfig);
  }
}
