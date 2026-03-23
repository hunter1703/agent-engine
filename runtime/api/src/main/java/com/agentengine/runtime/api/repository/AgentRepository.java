package com.agentengine.runtime.api.repository;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/** Repository for managing BaseAgentConfig entities */
@Singleton
public class AgentRepository extends AbstractMongoRepository<BaseAgentConfig> {

    @Inject
    public AgentRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
        super(mongoClientFactory, "Agent", BaseAgentConfig.class, validationService);
    }
}
