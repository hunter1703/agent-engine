package com.agentengine.util.mongodb.infra;

import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class InfraMongoRepository extends AbstractMongoRepository<InfraConfig> {

    @Inject
    public InfraMongoRepository(
            final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
        super(mongoClientFactory, "INFRA", "InfraConfig", InfraConfig.class, validationService);
    }
}
