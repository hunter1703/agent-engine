package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.AgentConfig;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;

/**
 * Repository for managing AgentConfig entities
 */
@Singleton
public class AgentRepository extends AbstractMongoRepository<AgentConfig> {

  public AgentRepository(MongoClientSupport mongoClientSupport) {
    super(mongoClientSupport, "Agent", AgentConfig.class);
  }
}