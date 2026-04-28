package com.agentengine.catalog.repository;

import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.AssetNotFoundException;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class ModelRepository extends AbstractMongoRepository<ModelConfig> {
    @Inject
    public ModelRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
        super(mongoClientFactory, AssetClass.MODEL, ModelConfig.class, validationService);
    }

    @Override
    public ModelConfig save(final ModelConfig modelConfig) {
        if (modelConfig == null) {
            throw new IllegalArgumentException("Model config is required");
        }
        if (StringUtils.isBlank(modelConfig.getId())) {
            return super.insert(modelConfig);
        }
        final ModelConfig existingModel = findById(modelConfig.getId());
        if (existingModel == null) {
            return super.insert(modelConfig);
        }
        return super.update(modelConfig.getId(), modelConfig);
    }

    @Override
    public ModelConfig update(final String id, final ModelConfig update) {
        final ModelConfig existingModel = findById(id);
        if (existingModel == null) {
            throw new AssetNotFoundException("Model", id);
        }
        return super.update(id, update);
    }
}
