package com.agentengine.catalog.repository;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentRepository extends AbstractMongoRepository<BaseAgentConfig> {
    @Inject
    public AgentRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
        super(mongoClientFactory, AssetClass.AGENT, BaseAgentConfig.class, validationService);
    }
}
