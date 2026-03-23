package com.agentengine.runtime.factories.context;

import com.agentengine.runtime.services.MongoSessionService;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.agents.beans.config.DefaultAgentConfig;
import com.agentengine.runtime.context.CompactionContextManager;
import com.agentengine.runtime.context.NoOpContextManager;
import com.agentengine.runtime.factories.model.ModelProvider;
import com.agentengine.runtime.context.ContextManager;
import com.agentengine.runtime.factories.context.ContextManagerFactory;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelConfig;
import com.agentengine.util.mongodb.infra.InfraMongoRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CompactionContextManagerFactory implements ContextManagerFactory<CompactionContextStrategyConfig, ContextManager> {

  private final ModelProvider modelProvider;
  private final MongoSessionService sessionService;
  private final InfraMongoRepository infraMongoRepository;

  @Inject
  public CompactionContextManagerFactory(final ModelProvider modelProvider, final MongoSessionService sessionService,
      final InfraMongoRepository infraMongoRepository) {
    this.modelProvider = modelProvider;
    this.sessionService = sessionService;
    this.infraMongoRepository = infraMongoRepository;
  }

  @Override
  public ContextManager build(final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    if (!config.isEnabled()) {
      return NoOpContextManager.INSTANCE;
    }
    final String modelId = resolveModelId(config, agentConfig);
    return new CompactionContextManager(config.getTokenThreshold(), config.getRecencyThreshold(), modelId, config.getPromptTemplate(),
        modelProvider, sessionService);
  }

  @Override
  public ContextManager build(final CompactionContextStrategyConfig config) {
    return build(config, new DefaultAgentConfig());
  }

  private String resolveModelId(final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
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
