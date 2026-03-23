package com.agentengine.core.repository;

import com.agentengine.core.utils.ModelUtils;
import com.agentengine.util.agents.beans.config.ModelConfig;
import com.agentengine.util.common.CollectionUtils;
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
  public ModelConfig insert(final ModelConfig modelConfig) {
    ModelUtils.generateServerConfig(modelConfig);
    return super.insert(modelConfig);
  }

  @Override
  public ModelConfig save(final ModelConfig modelConfig) {
    if (modelConfig == null) {
      throw new IllegalArgumentException("Model config is required");
    }
    if (StringUtils.isBlank(modelConfig.getId())) {
      ModelUtils.generateServerConfig(modelConfig);
      return super.insert(modelConfig);
    }
    final ModelConfig existingModel = findById(modelConfig.getId());
    if (existingModel == null) {
      ModelUtils.generateServerConfig(modelConfig);
      return super.insert(modelConfig);
    }
    applyServerConfigIfMissing(modelConfig, existingModel);
    return super.update(modelConfig.getId(), modelConfig);
  }

  @Override
  public ModelConfig update(final String id, final ModelConfig update) {
    final ModelConfig existingModel = findById(id);
    if (existingModel == null) {
      throw new AssetNotFoundException("Model", id);
    }
    applyServerConfigIfMissing(update, existingModel);
    return super.update(id, update);
  }

  private static void applyServerConfigIfMissing(final ModelConfig update, final ModelConfig existingModel) {
    if (ModelConfig.Provider.valueOfOrDefault(existingModel.getType()) != ModelConfig.Provider.OPEN_AI_COMPATIBLE) {
      return;
    }
    if (StringUtils.isBlank(update.getBaseUrl())) {
      update.setBaseUrl(existingModel.getBaseUrl());
    }
    if (StringUtils.isBlank(update.getServerCommand())) {
      update.setServerCommand(existingModel.getServerCommand());
    }
    if (CollectionUtils.isEmpty(update.getServerArgs())) {
      update.setServerArgs(existingModel.getServerArgs());
    }
    if (StringUtils.isBlank(update.getServerWorkdir())) {
      update.setServerWorkdir(existingModel.getServerWorkdir());
    }
  }
}
