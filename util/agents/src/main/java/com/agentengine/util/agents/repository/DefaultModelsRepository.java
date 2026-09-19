package com.agentengine.util.agents.repository;

import com.agentengine.util.agents.beans.config.DefaultModelsConfig;
import com.agentengine.util.infra.InfraConfigService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Optional;
import java.util.function.Function;

@Singleton
public class DefaultModelsRepository {

  private final InfraConfigService infraConfigService;

  @Inject
  public DefaultModelsRepository(final InfraConfigService infraConfigService) {
    this.infraConfigService = infraConfigService;
  }

  public String getTitleModelId() {
    return get(DefaultModelsConfig::getTitleModelId);
  }

  public String getCompactionModelId() {
    return get(DefaultModelsConfig::getCompactionModelId);
  }

  public String getEvaluatorModelId() {
    return get(DefaultModelsConfig::getEvaluatorModelId);
  }

  public String getEmbeddingModelId() {
    return get(DefaultModelsConfig::getEmbeddingModelId);
  }

  public String getChatModelId() {
    return get(DefaultModelsConfig::getChatModelId);
  }

  private String get(final Function<DefaultModelsConfig, String> getModelId) {
    final DefaultModelsConfig config = infraConfigService.get(DefaultModelsConfig.TYPE);
    return Optional.ofNullable(config).map(getModelId).orElse(null);
  }
}
