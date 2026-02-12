package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.ModelConfig;
import com.agentengine.engine.model.ModelUtils;
import com.google.adk.models.Model;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;

/**
 * Repository for managing ModelConfig entities
 */
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
  public ModelConfig update(String id, ModelConfig update) {
    final ModelConfig existingModel = findById(id).orElseThrow();
    if (ModelConfig.Provider.OPEN_AI_COMPATIBLE.name().equalsIgnoreCase(existingModel.getType())) {
      update.setBaseUrl(existingModel.getBaseUrl());
      update.setServerCommand(existingModel.getServerCommand());
      update.setServerArgs(existingModel.getServerArgs());
      update.setServerWorkdir(existingModel.getServerWorkdir());
    }
    return super.update(id, update);
  }
}