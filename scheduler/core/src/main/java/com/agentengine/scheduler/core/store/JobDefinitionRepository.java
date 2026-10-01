package com.agentengine.scheduler.core.store;

import com.agentengine.scheduler.api.models.JobDefinition;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Startup
public class JobDefinitionRepository extends AbstractRepository<JobDefinition> {

  @Inject
  public JobDefinitionRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.global(
                SchedulerDocumentStoreClientType.SCHEDULER, JobDefinition.class)),
        validationService);
  }
}
