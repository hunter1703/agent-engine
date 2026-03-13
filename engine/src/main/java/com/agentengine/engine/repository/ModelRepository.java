package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.utils.ModelUtils;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Repository for managing ModelConfig entities */
@Singleton
public class ModelRepository extends AbstractMongoRepository<ModelConfig> {

  @Inject
  public ModelRepository(final MongoClientFactory mongoClientFactory, ValidationService validationService) {
    super(mongoClientFactory, "Model", ModelConfig.class, validationService);
  }

  @Override
  public ModelConfig insert(ModelConfig modelConfig) {
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
    final ModelConfig existingModel = findById(modelConfig.getId()).orElse(null);
    if (existingModel == null) {
      ModelUtils.generateServerConfig(modelConfig);
      return super.insert(modelConfig);
    }
    applyServerConfigIfMissing(modelConfig, existingModel);
    return super.update(modelConfig.getId(), modelConfig);
  }

  @Override
  public ModelConfig update(String id, ModelConfig update) {
    final ModelConfig existingModel = findById(id).orElseThrow();
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
