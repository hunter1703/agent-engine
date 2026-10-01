package com.agentengine.agent.core.schedule;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.beans.AgentSchedule;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.PermissionChecker;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Startup
public class AgentScheduleRepository extends AbstractPermissionedRepository<AgentSchedule> {

  @Inject
  public AgentScheduleRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                AgentDocumentStoreClientType.AGENT, AgentSchedule.class)),
        validationService,
        permissionChecker,
        accessControlService);
  }
}
