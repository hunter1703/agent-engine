package com.agentengine.knowledge.core.chunking;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.util.agents.beans.config.ChunkingStrategy;
import com.agentengine.util.agents.beans.config.ChunkingType;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.cloudstorage.FileService;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.models.config.DefaultModels;
import com.agentengine.util.models.factories.ModelProvider;
import com.google.genai.types.Modality;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves a {@link Knowledge}'s configured chunking strategy into {@link IndexingStage}s (see
 * {@link #getConfiguredStages}), and assembles a full {@link ChunkingPipeline} from a
 * caller-supplied stage list (see {@link #create}).
 *
 * <p>An indexer whose file type needs stages beyond the configured strategy — reading a format no
 * strategy stage can parse directly, or describing embedded media — wraps or replaces {@link
 * #getConfiguredStages}'s result with its own (see {@code AbstractKnowledgeIndexer#stages} and its
 * overrides in {@code PdfKnowledgeIndexer}, {@code OfficeKnowledgeIndexer}, {@code
 * ImageKnowledgeIndexer}); this class only resolves the knowledge-configured part and appends the
 * {@link EmbeddingStage} that every pipeline ends with, so vectors are populated once all
 * splitting, merging, and describing is done.
 *
 * <p>Adding a new splitting/merging technique requires only:
 *
 * <ol>
 *   <li>A new {@link IndexingStage} implementation
 *   <li>A new {@link ChunkingType} enum value
 *   <li>A new case in {@link #toStages}
 * </ol>
 */
@Singleton
public class ChunkingPipelineFactory {

  private final ModelProvider modelProvider;
  private final FileService fileService;

  @Inject
  public ChunkingPipelineFactory(final ModelProvider modelProvider, final FileService fileService) {
    this.modelProvider = modelProvider;
    this.fileService = fileService;
  }

  public ChunkingPipeline create(
      final KnowledgeSettings settings,
      final RootIndexingStage rootStage,
      List<IndexingStage> stages) {
    stages = CollectionUtils.nullSafeMutableList(stages);
    stages.add(
        new EmbeddingStage(modelProvider, fileService, modalityVsEmbeddingModelIds(settings)));
    return new ChunkingPipeline(rootStage, stages);
  }

  /**
   * The ids of the models that embed each modality: the customer's default model of it, then the
   * knowledge's own, by real id.
   */
  private Map<String, List<String>> modalityVsEmbeddingModelIds(final KnowledgeSettings settings) {
    final Map<String, List<String>> configured =
        settings == null ? Map.of() : settings.getModalityVsEmbeddingModelIds();
    final Map<String, List<String>> modalityVsModelIds = new LinkedHashMap<>();
    modalityVsModelIds.put(
        Modality.Known.TEXT.name(),
        embeddingModelIds(
            DefaultModels.TEXT_EMBEDDING_ID, configured.get(Modality.Known.TEXT.name())));
    modalityVsModelIds.put(
        Modality.Known.IMAGE.name(),
        embeddingModelIds(
            DefaultModels.IMAGE_EMBEDDING_ID, configured.get(Modality.Known.IMAGE.name())));
    return modalityVsModelIds;
  }

  private List<String> embeddingModelIds(final String defaultModelId, final List<String> modelIds) {
    final Set<String> resolved = new LinkedHashSet<>();
    resolved.add(modelProvider.resolveEmbeddingModelId(defaultModelId));
    for (final String modelId : CollectionUtils.nullSafeList(modelIds)) {
      resolved.add(modelProvider.resolveEmbeddingModelId(modelId));
    }
    return List.copyOf(resolved);
  }

  public List<IndexingStage> getConfiguredStages(final Knowledge knowledge) {
    final KnowledgeSettings settings = knowledge.getSettings();
    List<ChunkingStrategy> strategies = settings != null ? settings.getChunkingStrategy() : null;
    strategies = CollectionUtils.isEmpty(strategies) ? List.of(new ChunkingStrategy()) : strategies;
    return strategies.stream().flatMap(strategy -> toStages(strategy).stream()).toList();
  }

  private List<IndexingStage> toStages(final ChunkingStrategy strategy) {
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
              new CosineBoundaryChunkingStage(maxSegmentSize, similarityThreshold, modelProvider));
      case LLM ->
          List.of(
              new LangchainSplitterStage(ChunkingType.PARAGRAPH, maxSegmentSize, maxOverlapSize),
              new MaxTokenChunkingStage(
                  approxCharsPerToken,
                  LlmChunkingStage.WINDOW_TOKEN_BUDGET / 5), // ~5 paragraphs per LLM window
              new LlmChunkingStage(approxCharsPerToken, DefaultModels.SMART_ID, modelProvider),
              new MaxTokenChunkingStage(approxCharsPerToken, maxTokensPerSegment));
      case RECURSIVE ->
          List.of(
              new LangchainSplitterStage(ChunkingType.RECURSIVE, maxSegmentSize, maxOverlapSize));
      default -> List.of(new FixedWindowChunkingStage(maxSegmentSize, maxOverlapSize));
    };
  }
}
