package com.agentengine.agent.core.services;

import com.agentengine.util.agents.beans.AgentSchedule;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class AgentScheduleAclServiceImpl extends AbstractAssetAclService<AgentSchedule>
    implements com.agentengine.agent.api.services.AgentScheduleAclService {

  @Inject
  public AgentScheduleAclServiceImpl(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                AgentDocumentStoreClientType.AGENT, AgentSchedule.class)));
  }
}
