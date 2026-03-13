package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoClient;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Repository for managing BaseAgentConfig entities */
@Singleton
public class AgentRepository extends AbstractMongoRepository<BaseAgentConfig> {

  @Inject
  public AgentRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, "Agent", BaseAgentConfig.class, validationService);
  }

  public AgentRepository(MongoClient mongoClient) {
    super(mongoClient, "Agent", BaseAgentConfig.class);
  }
}
