package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.knowledge.api.chunking.ChunkingPipeline;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.agents.beans.config.ChunkingStrategy;
import com.agentengine.util.agents.beans.config.ChunkingType;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * Assembles a {@link ChunkingPipeline} for a {@link Knowledge}, using its {@link
 * KnowledgeSettings}.
 *
 * <p>An {@link EmbeddingStage} is always appended as the final stage so that vectors are populated
 * once all splitting and merging is complete.
 *
 * <p>Adding a new technique requires only:
 *
 * <ol>
 *   <li>A new {@link ChunkingStage} implementation
 *   <li>A new {@link ChunkingType} enum value
 *   <li>A new case in {@link #toStages}
 * </ol>
 */
@Singleton
public class ChunkingPipelineFactory {

  private final ModelProvider modelProvider;
  private final DefaultModelsRepository defaultModelsRepository;

  @Inject
  public ChunkingPipelineFactory(
      final ModelProvider modelProvider, final DefaultModelsRepository defaultModelsRepository) {
    this.modelProvider = modelProvider;
    this.defaultModelsRepository = defaultModelsRepository;
  }

  public ChunkingPipeline create(final Knowledge knowledge) {
    final KnowledgeSettings settings = knowledge.getSettings();
    List<ChunkingStrategy> stages = settings != null ? settings.getChunkingStrategy() : null;
    stages = CollectionUtils.isEmpty(stages) ? List.of(new ChunkingStrategy()) : stages;

    final String embeddingModelId =
        resolveModelId(
            settings == null ? null : settings.getEmbeddingModelId(),
            defaultModelsRepository.getEmbeddingModelId());
    final String chatModelId =
        resolveModelId(
            settings == null ? null : settings.getChatModelId(),
            defaultModelsRepository.getChatModelId());

    final ChunkingPipeline.Builder builder = ChunkingPipeline.builder();
    for (final ChunkingStrategy stageStrategy : stages) {
      for (final ChunkingStage stage :
          toStages(knowledge, stageStrategy, embeddingModelId, chatModelId)) {
        builder.then(stage);
      }
    }
    builder.then(new EmbeddingStage(embeddingModelId, modelProvider));
    return builder.build();
  }

  private static String resolveModelId(final String configured, final String defaultModelId) {
    return StringUtils.isNotBlank(configured) ? configured : defaultModelId;
  }

  private List<ChunkingStage> toStages(
      final Knowledge knowledge,
      final ChunkingStrategy strategy,
      final String embeddingModelId,
      final String chatModelId) {
    final ChunkingType type = ChunkingType.valueOfOrDefault(strategy.getType());
    final int maxSegmentSize = strategy.getMaxSegmentSize();
    final int maxOverlapSize = strategy.getMaxOverlapSize();
    final double similarityThreshold = strategy.getSimilarityThreshold();
    final int maxTokensPerSegment = strategy.getMaxTokensPerSegment();
    final int approxCharsPerToken = strategy.getApproxCharsPerToken();
    return switch (type) {
      case SENTENCE, PARAGRAPH ->
          List.of(new LangchainSplitterStage(knowledge, type, maxSegmentSize, maxOverlapSize));
      case SEMANTIC ->
          List.of(
              new LangchainSplitterStage(
                  knowledge, ChunkingType.SENTENCE, maxSegmentSize, maxOverlapSize),
              new CosineBoundaryStage(
                  maxSegmentSize, similarityThreshold, embeddingModelId, modelProvider));
      case LLM ->
          List.of(
              new LangchainSplitterStage(
                  knowledge, ChunkingType.PARAGRAPH, maxSegmentSize, maxOverlapSize),
              new MaxTokenCapStage(
                  approxCharsPerToken,
                  LlmBoundaryStage.WINDOW_TOKEN_BUDGET / 5), // ~5 paragraphs per LLM window
              new LlmBoundaryStage(approxCharsPerToken, chatModelId, modelProvider),
              new MaxTokenCapStage(approxCharsPerToken, maxTokensPerSegment));
      case RECURSIVE ->
          List.of(
              new LangchainSplitterStage(
                  knowledge, ChunkingType.RECURSIVE, maxSegmentSize, maxOverlapSize));
      default -> List.of(new FixedWindowSplitterStage(knowledge, maxSegmentSize, maxOverlapSize));
    };
  }
}
