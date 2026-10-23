package com.agentengine.agent.infra.factories.context;

import com.agentengine.agent.infra.compaction.LLMSummarizer;
import com.agentengine.agent.infra.compaction.DefaultEventCompactor;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.agents.beans.config.CompactionContextStrategyConfig;
import com.agentengine.util.agents.beans.config.ContextStrategyConfig;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.models.config.DefaultModels;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Builds a {@link DefaultEventCompactor} with a {@link LLMSummarizer}: once the context passes the
 * trigger threshold, everything but the latest events is summarized by a model.
 */
@Singleton
public class DefaultCompactorFactory implements CompactorFactory<CompactionContextStrategyConfig> {

  private final ModelProvider modelProvider;

  @Inject
  public DefaultCompactorFactory(final ModelProvider modelProvider) {
    this.modelProvider = modelProvider;
  }

  @Override
  public DefaultEventCompactor build(
      final CompactionContextStrategyConfig config, final BaseAgentConfig agentConfig) {
    return new DefaultEventCompactor(
        new LLMSummarizer(
            modelProvider,
            agentConfig.getModelId(),
            resolveModelId(config),
            config.getPromptTemplate()),
        modelProvider,
        agentConfig.getModelId(),
        config);
  }

  @Override
  public String type() {
    return ContextStrategyConfig.ContextStrategyType.DEFAULT.type();
  }

  private static String resolveModelId(final CompactionContextStrategyConfig config) {
    return StringUtils.isNotBlank(config.getModelId())
        ? config.getModelId()
        : DefaultModels.FAST_ID;
  }
}
