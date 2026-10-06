package com.agentengine.agent.infra.notebook;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.repository.AgentDocumentStoreClientType;
import com.agentengine.util.common.beans.AssetClass;
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
  protected void requireCreatePermission(final List<Notebook> notebooks) {}

  /**
   * A notebook belongs to the session it is made in, for every user acting in it.
   *
   * @throws IllegalArgumentException when the creator acts in no session
   */
  @Override
  protected List<SharingChange> getInitialShare(final Notebook notebook) {
    return Context.currentPrincipal()
        .map(
            creator ->
                List.of(
                    buildShare(
                        notebook,
                        creator.actingIn(AssetClass.AGENT_SESSION).forAnyUser().toString(),
                        StandardRole.OWNER)))
        .orElse(List.of());
  }

  @Override
  protected java.util.Map<String, com.agentengine.util.common.beans.Acl> shareNewEntities(
      final List<Notebook> entities) {
    forgetAcls(entities.stream().map(Notebook::getId).toList());
    return super.shareNewEntities(entities);
  }
}
