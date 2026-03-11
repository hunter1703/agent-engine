package com.agentengine.engine.factories.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.CompactionContextStrategyConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.factories.ContextManagerFactory;
import com.agentengine.engine.context.CompactionContextManager;
import com.agentengine.engine.context.NoOpContextManager;
import com.agentengine.engine.factories.model.ModelProvider;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CompactionContextManagerFactory
    implements ContextManagerFactory<CompactionContextStrategyConfig, ContextManager> {

  private final ModelProvider modelProvider;
  private final AgentSessionRepository sessionRepository;
  private final InfraMongoRepository infraMongoRepository;

  @Inject
  public CompactionContextManagerFactory(
      final ModelProvider modelProvider,
      final AgentSessionRepository sessionRepository,
      final InfraMongoRepository infraMongoRepository) {
    this.modelProvider = modelProvider;
    this.sessionRepository = sessionRepository;
    this.infraMongoRepository = infraMongoRepository;
  }

  @Override
  public ContextManager build(
      final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    if (!config.isEnabled()) {
      return NoOpContextManager.INSTANCE;
    }
    final String modelId = resolveModelId(config, agentConfig);
    return new CompactionContextManager(
        config.getTokenThreshold(),
        config.getRecencyThreshold(),
        modelId,
        config.getPromptTemplate(),
        modelProvider,
        sessionRepository);
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
    final DefaultModelConfig defaults = infraMongoRepository.findOneByType(DefaultModelConfig.TYPE);
    if (defaults != null && StringUtils.isNotBlank(defaults.getCompactionModelId())) {
      return defaults.getCompactionModelId();
    }
    return agentConfig.getModelId();
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.COMPACTION.type();
  }
}
