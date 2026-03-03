package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.AgentConfig;
import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Repository for managing AgentConfig entities */
@Singleton
public class AgentRepository extends AbstractMongoRepository<AgentConfig> {

  @Inject
  public AgentRepository(MongoClientFactory mongoClientFactory) {
    super(mongoClientFactory, "Agent", AgentConfig.class);
  }

  public AgentRepository(MongoClient mongoClient) {
    super(mongoClient, "Agent", AgentConfig.class);
  }
}
