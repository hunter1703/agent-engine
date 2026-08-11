package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.Cache;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class InfraConfigServiceImpl implements InfraConfigService {

    private final Cache<String, InfraConfig> cache;

    @Inject
    public InfraConfigServiceImpl(final InfraMongoRepository repository) {
        this.cache = new Cache<>(CacheBuilder.newBuilder().maximumSize(256), repository::findById);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T extends InfraConfig> T findById(
            final String configCategory, final String configType, final String configId) {
        return (T) cache.get(configCategory + ":" + configType + ":" + configId);
    }
}
