package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.validation.ConfigValidationService;
import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Repository for managing BaseAgentConfig entities */
@Singleton
public class AgentRepository extends AbstractMongoRepository<BaseAgentConfig> {

  @Inject
  public AgentRepository(final MongoClientFactory mongoClientFactory, final ConfigValidationService validationService) {
    super(mongoClientFactory, "Agent", BaseAgentConfig.class, validationService);
  }

  public AgentRepository(MongoClient mongoClient) {
    super(mongoClient, "Agent", BaseAgentConfig.class);
  }
}
