package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.mongodb.client.MongoClient;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Repository for managing AgentConfig entities
 */
@Singleton
public class AgentRepository extends AbstractMongoRepository<AgentConfig> {

  @Inject
  public AgentRepository(MongoClientSupport mongoClientSupport) {
    super(mongoClientSupport, "Agent", AgentConfig.class);
  }

  public AgentRepository(MongoClient mongoClient) {
    super(mongoClient, "Agent", AgentConfig.class);
  }
}