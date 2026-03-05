package com.agentengine.engine.builders.context;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.api.beans.config.AgentModelConfig;
import com.agentengine.engine.api.beans.config.ContextManagerConfig;
import com.agentengine.engine.api.beans.config.SummarizeContextManagerConfig;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.api.builders.ContextManagerBuilder;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.context.SessionContextSummaryService;
import com.agentengine.engine.context.SummarizingContextManager;
import com.agentengine.engine.infra.DefaultModelConfig;
import com.agentengine.engine.repository.InfraMongoRepository;
import com.google.adk.models.BaseLlm;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class SummarizeContextManagerBuilder
    implements ContextManagerBuilder<SummarizeContextManagerConfig, SummarizingContextManager> {
  private static final Logger LOG = LoggerFactory.getLogger(SummarizeContextManagerBuilder.class);
  private final ModelProvider modelProvider;
  private final InfraMongoRepository infraMongoRepository;
  private final SessionContextSummaryService summaryService;

  public SummarizeContextManagerBuilder(
      final ModelProvider modelProvider,
      final InfraMongoRepository infraMongoRepository,
      final SessionContextSummaryService summaryService) {
    this.modelProvider = modelProvider;
    this.infraMongoRepository = infraMongoRepository;
    this.summaryService = summaryService;
  }

  @Override
  public SummarizingContextManager build(final SummarizeContextManagerConfig contextConfig) {
    throw new UnsupportedOperationException(
        "ContextManagerBuilder must be built with agent config to resolve summary model.");
  }

  @Override
  public SummarizingContextManager build(
      final SummarizeContextManagerConfig contextConfig, final AgentConfig agentConfig) {
    final String summaryModelId = resolveSummaryModelId(contextConfig, agentConfig.getModel().getModelId());
    final BaseLlm summaryModel = resolveModel(summaryModelId);
    return new SummarizingContextManager(
        contextConfig.getTriggerTokens(),
        contextConfig.getRecencyTokens(),
        summaryModelId,
        summaryModel,
        summaryService);
  }

  @Override
  public String type() {
    return ContextManagerConfig.ContextType.SUMMARIZE.name().toLowerCase();
  }

  private String resolveSummaryModelId(
      final SummarizeContextManagerConfig contextConfig, final String defaultModelId) {
    if (contextConfig != null && StringUtils.isNotBlank(contextConfig.getSummaryModelId())) {
      return contextConfig.getSummaryModelId();
    }
    try {
      final DefaultModelConfig infraConfig = infraMongoRepository.findOneByType(DefaultModelConfig.TYPE);
      if (infraConfig != null && StringUtils.isNotBlank(infraConfig.getSummaryModelId())) {
        return infraConfig.getSummaryModelId();
      }
    } catch (Exception ex) {
      LOG.warn("Failed to load infra default summary model config; falling back to agent model.");
    }
    return defaultModelId;
  }

  private BaseLlm resolveModel(final String modelId) {
    if (StringUtils.isBlank(modelId)) {
      return null;
    }
    try {
      final AgentModelConfig modelConfig = new AgentModelConfig();
      modelConfig.setModelId(modelId);
      modelConfig.setRole("context_summarizer");
      modelConfig.setSystemPrompt(
          "Summarize conversation history accurately and concisely without inventing facts.");
      return modelProvider.get(modelConfig);
    } catch (Exception ex) {
      LOG.warn("Failed to resolve summary model '{}' for context compaction.", modelId, ex);
      return null;
    }
  }
}
