package com.agentengine.agent.infra.factories.context;

import com.agentengine.agent.infra.compaction.LLMSummarizer;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.adk.summarizer.EventCompactor;
import com.google.adk.summarizer.TailRetentionEventCompactor;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Builds ADK's {@link TailRetentionEventCompactor} with a {@link LLMSummarizer}: once the history
 * passes the token threshold, everything but the last events is summarized by a model.
 */
@Singleton
public class LLMCompactorFactory implements CompactorFactory<CompactionContextStrategyConfig> {

  private final ModelProvider modelProvider;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public LLMCompactorFactory(
      final ModelProvider modelProvider, final DefaultModelsRepository defaultModelsRepository) {
    this.modelProvider = modelProvider;
    this.defaultModelsRepository = defaultModelsRepository;
  }

  @Override
  public EventCompactor build(
      final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    return new TailRetentionEventCompactor(
        new LLMSummarizer(
            modelProvider, resolveModelId(config, agentConfig), config.getPromptTemplate()),
        config.getKeepLastEvents(),
        config.getTokenThreshold());
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.COMPACTION.type();
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
}
