package com.agentengine.knowledge.core.chunking;

import com.agentengine.agent.infra.factories.model.EmbeddingModelFactory;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.knowledge.api.chunking.ChunkingPipeline;
import com.agentengine.knowledge.api.chunking.ChunkingStage;
import com.agentengine.util.agents.beans.config.ChunkingStrategy;
import com.agentengine.util.agents.beans.config.ChunkingType;
import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.function.Function;

/**
 * Assembles a {@link ChunkingPipeline} from a {@link KnowledgeSettings}.
 *
 * <p>An {@link EmbeddingStage} is always appended as the final stage so that vectors are
 * populated once all splitting and merging is complete.
 *
 * <p>Adding a new technique requires only:
 * <ol>
 *   <li>A new {@link ChunkingStage} implementation
 *   <li>A new {@link ChunkingType} enum value
 *   <li>A new case in {@link #toStages}
 * </ol>
 */
@Singleton
public class ChunkingPipelineFactory {

    private final EmbeddingModelFactory embeddingModelFactory;
    private final ModelProvider modelProvider;
    private final InfraConfigService infraConfigService;

    public ChunkingPipelineFactory(
            final EmbeddingModelFactory embeddingModelFactory,
            final ModelProvider modelProvider,
            final InfraConfigService infraConfigService) {
        this.embeddingModelFactory = embeddingModelFactory;
        this.modelProvider = modelProvider;
        this.infraConfigService = infraConfigService;
    }

    public ChunkingPipeline create(final KnowledgeSettings settings) {
        List<ChunkingStrategy> stages = settings != null ? settings.getChunkingStrategy() : null;
        stages = CollectionUtils.isEmpty(stages) ? List.of(new ChunkingStrategy()) : stages;

        final DefaultModelsConfig defaults = loadDefaults(settings);
        final String embeddingModelId = resolveModelId(
                settings == null ? null : settings.getEmbeddingModelId(),
                defaults,
                DefaultModelsConfig::getEmbeddingModelId);
        final String chatModelId = resolveModelId(
                settings == null ? null : settings.getChatModelId(), defaults, DefaultModelsConfig::getChatModelId);

        final ChunkingPipeline.Builder builder = ChunkingPipeline.builder();
        for (final ChunkingStrategy stageStrategy : stages) {
            for (final ChunkingStage stage : toStages(stageStrategy, embeddingModelId, chatModelId)) {
                builder.then(stage);
            }
        }
        builder.then(new EmbeddingStage(embeddingModelId, embeddingModelFactory));
        return builder.build();
    }

    /** Loads defaults only when at least one model ID is missing from settings. */
    private DefaultModelsConfig loadDefaults(final KnowledgeSettings settings) {
        if (settings != null
                && StringUtils.isNotBlank(settings.getEmbeddingModelId())
                && StringUtils.isNotBlank(settings.getChatModelId())) {
            return null;
        }
        return infraConfigService.findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID);
    }

    private static String resolveModelId(
            final String configured,
            final DefaultModelsConfig defaults,
            final Function<DefaultModelsConfig, String> extractor) {
        if (StringUtils.isNotBlank(configured)) {
            return configured;
        }
        return defaults != null ? extractor.apply(defaults) : null;
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
            case SENTENCE, PARAGRAPH -> List.of(new LangchainSplitterStage(type, maxSegmentSize, maxOverlapSize));
            case SEMANTIC ->
                List.of(
                        new LangchainSplitterStage(ChunkingType.SENTENCE, maxSegmentSize, maxOverlapSize),
                        new CosineBoundaryStage(
                                maxSegmentSize, similarityThreshold, embeddingModelId, embeddingModelFactory));
            case LLM ->
                List.of(
                        new LangchainSplitterStage(ChunkingType.PARAGRAPH, maxSegmentSize, maxOverlapSize),
                        new MaxTokenCapStage(
                                approxCharsPerToken,
                                LlmBoundaryStage.WINDOW_TOKEN_BUDGET / 5), // ~5 paragraphs per LLM window
                        new LlmBoundaryStage(approxCharsPerToken, chatModelId, modelProvider),
                        new MaxTokenCapStage(approxCharsPerToken, maxTokensPerSegment));
            default -> List.of(new LangchainSplitterStage(ChunkingType.RECURSIVE, maxSegmentSize, maxOverlapSize));
        };
    }
}
