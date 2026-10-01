package com.agentengine.agent.infra.notebook;

import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Startup
public class NotesRepository extends AbstractRepository<Note> {

  @Inject
  public NotesRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(AgentDocumentStoreClientType.AGENT, Note.class)),
        validationService);
  }
}
