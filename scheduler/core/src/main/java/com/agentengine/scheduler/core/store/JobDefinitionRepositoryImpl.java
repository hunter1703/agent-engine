package com.agentengine.scheduler.core.store;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JobDefinitionRepositoryImpl extends AbstractMongoRepository<JobDefinition>
        implements JobDefinitionRepository {

    @Inject
    public JobDefinitionRepositoryImpl(
            final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
        super(mongoClientFactory, AssetClass.JOB_DEFINITION, JobDefinition.class, validationService);
    }
}
