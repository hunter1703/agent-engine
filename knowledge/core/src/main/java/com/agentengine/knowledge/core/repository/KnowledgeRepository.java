package com.agentengine.knowledge.core.repository;

import com.agentengine.knowledge.api.beans.Knowledge;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class KnowledgeRepository extends AbstractMongoRepository<Knowledge> {

    @Inject
    public KnowledgeRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
        super(mongoClientFactory, AssetClass.KNOWLEDGE, Knowledge.class, validationService);
    }
}
