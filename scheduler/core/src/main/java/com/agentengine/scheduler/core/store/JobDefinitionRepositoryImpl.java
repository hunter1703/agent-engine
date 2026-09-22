package com.agentengine.scheduler.core.store;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.scheduler.api.store.JobDefinitionRepository;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.GlobalMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class JobDefinitionRepositoryImpl extends GlobalMongoRepository<JobDefinition>
    implements JobDefinitionRepository {

  @Inject
  public JobDefinitionRepositoryImpl(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(
        mongoClientFactory,
        SchedulerMongoStoreClientType.SCHEDULER,
        JobDefinition.class,
        validationService);
  }
}
