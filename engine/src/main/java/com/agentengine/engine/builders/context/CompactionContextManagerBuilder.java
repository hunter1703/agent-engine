package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.ContextManager;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.api.beans.config.CompactionContextStrategyConfig;
import com.agentengine.engine.api.beans.config.ContextStrategyConfig;
import com.agentengine.engine.api.beans.config.DefaultAgentConfig;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.context.CompactionContextManager;
import com.agentengine.engine.context.NoOpContextManager;
import com.agentengine.engine.infra.DefaultModelConfig;
import com.agentengine.engine.repository.AgentSessionRepository;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.agentengine.util.common.StringUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CompactionContextManagerBuilder
    implements ContextManagerBuilder<CompactionContextStrategyConfig, ContextManager> {

  private final ModelProvider modelProvider;
  private final AgentSessionRepository sessionRepository;
  private final InfraMongoRepository infraMongoRepository;

  @Inject
  public CompactionContextManagerBuilder(
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
    return ContextStrategyConfig.ContextStrategyType.COMPACTION.name().toLowerCase();
  }
}
