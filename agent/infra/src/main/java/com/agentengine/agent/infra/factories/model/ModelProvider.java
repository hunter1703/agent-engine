package com.agentengine.agent.infra.factories.model;

import com.agentengine.catalog.api.services.ModelService;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.RefCountedCache;
import com.agentengine.util.common.StringUtils;
import com.google.adk.models.BaseLlm;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ModelProvider {
    private static final Logger LOG = LoggerFactory.getLogger(ModelProvider.class);

    private final Map<String, ModelFactory<?>> typeVsFactory;
    private final ModelFactory<?> defaultFactory;
    private final ModelService modelService;
    private final RefCountedCache<String, BaseLlm> cache;

    @Inject
    public ModelProvider(
            final Instance<ModelFactory<?>> allFactories,
            final OpenAIModelFactory openAIModelFactory,
            final ModelService modelService) {
        this.typeVsFactory =
                CollectionUtils.transformToMap(allFactories.stream().toList(), ModelFactory::type, Function.identity());
        this.defaultFactory = openAIModelFactory;
        this.modelService = modelService;
        this.cache = RefCountedCache.<String, BaseLlm>builder()
                .name("model-provider")
                .idleTimeout(15, TimeUnit.MINUTES)
                .cleanupInterval(60, TimeUnit.SECONDS)
                .creator(this::buildModel)
                .onEvict((_, model) -> tryClose(model))
                .build();
    }

    public Flowable<LlmResponse> invokeAcquiring(
            final String modelId, Function<BaseLlm, Flowable<LlmResponse>> invocation) {
        final BaseLlm model = acquire(modelId);
        try {
            return invocation.apply(model);
        } finally {
            release(modelId);
        }
    }

    public BaseLlm acquire(final String modelId) {
        if (StringUtils.isBlank(modelId)) {
            throw new IllegalArgumentException("modelId cannot be blank");
        }
        return cache.getAndAcquire(modelId);
    }

    public void release(final String modelId) {
        if (StringUtils.isBlank(modelId)) {
            return;
        }
        cache.release(modelId);
    }

    private BaseLlm buildModel(final String modelId) {
        final ModelConfig config = modelService.getModel(modelId);
        if (config == null) {
            throw new IllegalStateException("Model config missing for model_id=" + modelId);
        }
        final ModelFactory<?> factory = typeVsFactory.getOrDefault(config.getProvider(), defaultFactory);
        return factory.build(config);
    }

    private static void tryClose(final BaseLlm model) {
        if (model instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception ex) {
                LOG.debug("Failed to close model during cache eviction.", ex);
            }
        }
    }
}
