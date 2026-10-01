package com.agentengine.agent.infra.notebook;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.context.Context;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

@Singleton
@Startup
public class NotebookRepository extends AbstractPermissionedRepository<Notebook> {

  @Inject
  public NotebookRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(AgentDocumentStoreClientType.AGENT, Notebook.class)),
        validationService,
        permissionChecker,
        accessControlService);
  }

  /**
   * Notebooks are created only through the notebook tools, so an agent given those tools may create
   * one; no permission on every asset is taken.
   */
  @Override
  protected void canCreate(final List<Notebook> notebooks) {}

  /** A notebook belongs to the session it is made in, for every user acting in it. */
  @Override
  protected List<SharingChange> getInitialShare(final Notebook notebook) {
    return Context.currentPrincipal()
        .map(
            creator ->
                List.of(buildShare(notebook, creator.forAnyUser().toString(), StandardRole.OWNER)))
        .orElse(List.of());
  }
}
