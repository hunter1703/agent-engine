package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.util.agents.beans.config.ChunkingStrategy;
import com.agentengine.util.agents.beans.config.ChunkingType;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.agents.repository.DefaultModelsRepository;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.models.factories.ModelProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * Resolves a {@link Knowledge}'s configured chunking strategy into {@link ChunkingStage}s (see
 * {@link #getStages}), and assembles a full {@link ChunkingPipeline} from a caller-supplied stage
 * list (see {@link #create}).
 *
 * <p>An indexer whose file type needs stages beyond the configured strategy — reading a format no
 * strategy stage can parse directly, or describing embedded media — wraps or replaces {@link
 * #getStages}'s result with its own (see {@code AbstractTextKnowledgeIndexer#stages} and its
 * overrides in {@code PdfKnowledgeIndexer}, {@code OfficeKnowledgeIndexer}, {@code
 * ImageKnowledgeIndexer}); this class only resolves the knowledge-configured part and appends the
 * terminal {@link EmbeddingStage} that every pipeline ends with, so vectors are populated once all
 * splitting, merging, and describing is done.
 *
 * <p>Adding a new splitting/merging technique requires only:
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
  private final FileService fileService;

  @Inject
  public ChunkingPipelineFactory(
      final ModelProvider modelProvider,
      final DefaultModelsRepository defaultModelsRepository,
      final FileService fileService) {
    this.modelProvider = modelProvider;
    this.defaultModelsRepository = defaultModelsRepository;
    this.fileService = fileService;
  }

  public ChunkingPipeline create(final Knowledge knowledge, final List<ChunkingStage> stages) {
    final ChunkingPipeline.Builder builder = ChunkingPipeline.builder(knowledge, fileService);
    stages.forEach(builder::then);
    builder.then(new EmbeddingStage(embeddingModelId(knowledge), modelProvider));
    return builder.build();
  }

  public List<ChunkingStage> getStages(final Knowledge knowledge) {
    final KnowledgeSettings settings = knowledge.getSettings();
    List<ChunkingStrategy> strategies = settings != null ? settings.getChunkingStrategy() : null;
    strategies = CollectionUtils.isEmpty(strategies) ? List.of(new ChunkingStrategy()) : strategies;

    final String embeddingModelId = embeddingModelId(knowledge);
    final String chatModelId =
        StringUtils.getOrDefault(
            settings == null ? null : settings.getChatModelId(),
            defaultModelsRepository.getChatModelId());

    return strategies.stream()
        .flatMap(strategy -> toStages(strategy, embeddingModelId, chatModelId).stream())
        .toList();
  }

  private String embeddingModelId(final Knowledge knowledge) {
    final KnowledgeSettings settings = knowledge.getSettings();
    return StringUtils.getOrDefault(
        settings == null ? null : settings.getEmbeddingModelId(),
        defaultModelsRepository.getEmbeddingModelId());
  }

  private List<ChunkingStage> toStages(
      final ChunkingStrategy strategy, final String embeddingModelId, final String chatModelId) {
    final ChunkingType type = ChunkingType.valueOfOrDefault(strategy.getType());
    final int maxSegmentSize = strategy.getMaxSegmentSize();
    final int maxOverlapSize = strategy.getMaxOverlapSize();
    final double similarityThreshold = strategy.getSimilarityThreshold();
    final int maxTokensPerSegment = strategy.getMaxTokensPerSegment();
    final int approxCharsPerToken = strategy.getApproxCharsPerToken();
    return switch (type) {
      case SENTENCE, PARAGRAPH ->
          List.of(new LangchainSplitterStage(type, maxSegmentSize, maxOverlapSize));
      case SEMANTIC ->
          List.of(
              new LangchainSplitterStage(ChunkingType.SENTENCE, maxSegmentSize, maxOverlapSize),
              new CosineBoundaryStage(
                  maxSegmentSize, similarityThreshold, embeddingModelId, modelProvider));
      case LLM ->
          List.of(
              new LangchainSplitterStage(ChunkingType.PARAGRAPH, maxSegmentSize, maxOverlapSize),
              new MaxTokenCapStage(
                  approxCharsPerToken,
                  LlmBoundaryStage.WINDOW_TOKEN_BUDGET / 5), // ~5 paragraphs per LLM window
              new LlmBoundaryStage(approxCharsPerToken, chatModelId, modelProvider),
              new MaxTokenCapStage(approxCharsPerToken, maxTokensPerSegment));
      case RECURSIVE ->
          List.of(
              new LangchainSplitterStage(ChunkingType.RECURSIVE, maxSegmentSize, maxOverlapSize));
      default -> List.of(new FixedWindowSplitterStage(maxSegmentSize, maxOverlapSize));
    };
  }
}
