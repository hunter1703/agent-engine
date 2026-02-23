package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.model.ModelUtils;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;

/** Repository for managing ModelConfig entities */
@Singleton
public class ModelRepository extends AbstractMongoRepository<ModelConfig> {

  public ModelRepository(MongoClientSupport mongoClientSupport) {
    super(mongoClientSupport, "Model", ModelConfig.class);
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

  private static void applyServerConfigIfMissing(
      final ModelConfig update, final ModelConfig existingModel) {
    if (!ModelConfig.Provider.OPEN_AI_COMPATIBLE.matches(existingModel.getType())) {
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
