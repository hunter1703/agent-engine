package com.agentengine.agent.infra.factories.context;

import com.agentengine.agent.infra.context.CompactionContextManager;
import com.agentengine.agent.infra.context.ContextManager;
import com.agentengine.catalog.api.services.SessionService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.agents.beans.config.DefaultAgentConfig;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CompactionContextManagerFactory
    implements ContextManagerFactory<CompactionContextStrategyConfig, ContextManager> {

  private final ModelProvider modelProvider;
  private final SessionService sessionService;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public CompactionContextManagerFactory(
      final ModelProvider modelProvider,
      final SessionService sessionService,
      final DefaultModelsRepository defaultModelsRepository) {
    this.modelProvider = modelProvider;
    this.sessionService = sessionService;
    this.defaultModelsRepository = defaultModelsRepository;
  }

  @Override
  public ContextManager build(
      final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    return new CompactionContextManager(
        config.getTokenThreshold(),
        config.getRecencyThreshold(),
        resolveModelId(config, agentConfig),
        config.getPromptTemplate(),
        modelProvider,
        sessionService);
  }

  @Override
  public ContextManager build(final CompactionContextStrategyConfig config) {
    return build(config, new DefaultAgentConfig());
  }

  private String resolveModelId(
      final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    if (StringUtils.isNotBlank(config.getModelId())) {
      return config.getModelId();
    }
    final String defaultModelId = defaultModelsRepository.getCompactionModelId();
    if (StringUtils.isNotBlank(defaultModelId)) {
      return defaultModelId;
    }
    return agentConfig.getModelId();
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.COMPACTION.type();
  }
}
