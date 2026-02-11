package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.ModelConfig;
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
}