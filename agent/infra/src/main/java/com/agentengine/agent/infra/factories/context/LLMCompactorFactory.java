package com.agentengine.agent.infra.factories.context;

import com.agentengine.agent.infra.compaction.LLMSummarizer;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.config.DefaultModels;
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

  @Inject
  public LLMCompactorFactory(final ModelProvider modelProvider) {
    this.modelProvider = modelProvider;
  }

  @Override
  public EventCompactor build(
      final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    return new TailRetentionEventCompactor(
        new LLMSummarizer(modelProvider, resolveModelId(config), config.getPromptTemplate()),
        config.getKeepLastEvents(),
        config.getTokenThreshold());
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.COMPACTION.type();
  }

  private static String resolveModelId(final CompactionContextStrategyConfig config) {
    return StringUtils.isNotBlank(config.getModelId()) ? config.getModelId() : DefaultModels.FAST_ID;
  }
}
