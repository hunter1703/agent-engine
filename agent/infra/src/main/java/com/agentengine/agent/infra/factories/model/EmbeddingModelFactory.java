package com.agentengine.agent.infra.factories.model;

import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.util.agents.beans.config.EmbeddingModelConfig;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.RefCountedCache;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.mongodb.infra.DefaultModelsConfig;
import com.agentengine.util.mongodb.infra.InfraConfigService;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Resolves and constructs an {@link EmbeddingModel} from a stored {@link EmbeddingModelConfig},
 * following the same config-driven pattern as the chat model factories.
 *
 * <p>Resolution order for the model ID:
 * <ol>
 *   <li>The provided {@code modelId} if non-blank
 *   <li>{@link DefaultModelsConfig#getEmbeddingModelId()} from infra config
 * </ol>
 *
 * <p>The resolved ID is used to load an {@link EmbeddingModelConfig} from {@link ModelService},
 * which carries the provider, baseUrl, apiKey, and model name — identical to how chat models
 * are configured.
 */
@Singleton
public class EmbeddingModelFactory {
    private static final Logger LOGGER = LoggerFactory.getLogger(EmbeddingModelFactory.class);

    private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);

    private final RefCountedCache<String, EmbeddingModel> cache;
    private final InfraConfigService infraConfigService;

    @Inject
    public EmbeddingModelFactory(final ModelService modelService, final InfraConfigService infraConfigService) {
        this.infraConfigService = infraConfigService;
        this.cache = RefCountedCache.<String, EmbeddingModel>builder()
                .name("model-provider")
                .idleTimeout(15, TimeUnit.MINUTES)
                .cleanupInterval(60, TimeUnit.SECONDS)
                .creator(modelId -> {
                    final String resolvedId = resolveModelId(modelId);
                    final EmbeddingModelConfig config = (EmbeddingModelConfig) modelService.getModel(resolvedId);
                    return build(config);
                })
                .onEvict((key, model) ->
                        LOGGER.info("Evicting embedding model : {} for key : {}", model.modelName(), key))
                .build();
    }

    public EmbeddingModel get(final String modelId) {
        return cache.getAndAcquire(modelId);
    }

    public void release(final String modelId) {
        cache.release(modelId);
    }

    /** Returns the effective embedding model ID, resolving from infra defaults when {@code modelId} is blank. */
    public String resolveDefaultModelId() {
        return resolveModelId(null);
    }

    private String resolveModelId(final String modelId) {
        if (StringUtils.isNotBlank(modelId)) {
            return modelId;
        }
        final DefaultModelsConfig defaults =
                infraConfigService.findById(DefaultModelsConfig.CATEGORY, DefaultModelsConfig.CONFIG_ID);
        return defaults != null ? defaults.getEmbeddingModelId() : null;
    }

    private static EmbeddingModel build(final EmbeddingModelConfig config) {
        final ModelConfig.Provider provider = ModelConfig.Provider.fromType(config.getProvider());
        final String baseUrl = config.getBaseUrl();
        final String model = config.getModel();
        return switch (provider) {
            case OLLAMA ->
                OllamaEmbeddingModel.builder()
                        .baseUrl(baseUrl)
                        .modelName(model)
                        .timeout(DEFAULT_TIMEOUT)
                        .build();
            case OPEN_AI_COMPATIBLE ->
                OpenAiEmbeddingModel.builder()
                        .baseUrl(baseUrl)
                        .apiKey(config.getApiKey())
                        .modelName(model)
                        .timeout(DEFAULT_TIMEOUT)
                        .build();
            default -> throw new IllegalArgumentException("Unsupported embedding model provider: " + provider);
        };
    }
}
