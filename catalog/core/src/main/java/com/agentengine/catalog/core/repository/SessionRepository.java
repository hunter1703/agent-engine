package com.agentengine.catalog.core.repository;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.context.Context;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.agentengine.util.tenancy.SharingChange;
import com.agentengine.util.tenancy.StandardRole;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Singleton
@Startup
public class SessionRepository extends AbstractPermissionedRepository<AgentSession> {

  private final AgentRepository agentRepository;

  @Inject
  public SessionRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService,
      final AgentRepository agentRepository) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                CatalogDocumentStoreClientType.CATALOG, AgentSession.class)),
        validationService,
        permissionChecker,
        accessControlService);
    this.agentRepository = agentRepository;
  }

  /**
   * Starting a session is a use of its agent, so it takes READ on the agent. A child session is
   * started by its parent's run for a sub-agent the parent's agent lists, and whoever listed it was
   * checked for using it then, so it takes acting in the parent session instead — or in the child
   * session itself, which re-creates its own record when it recovers without one.
   */
  @Override
  protected void requireCreatePermission(final List<AgentSession> sessions) {
    final Set<String> agentIds = new LinkedHashSet<>();
    for (final AgentSession session : sessions) {
      if (StringUtils.isBlank(session.getParentSessionId())) {
        agentIds.add(session.getAgentId());
      } else if (!isCreatedWithinParentOrItself(session)) {
        throw new UnauthorizedException(AssetClass.AGENT_SESSION, session.getParentSessionId());
      }
    }
    final Set<String> permittedIds = agentRepository.findPermittedIds(agentIds, Permission.READ);
    for (final String agentId : agentIds) {
      if (!permittedIds.contains(agentId)) {
        throw new UnauthorizedException(AssetClass.AGENT, agentId);
      }
    }
  }

  @Override
  protected List<SharingChange> getInitialShare(final AgentSession session) {
    final List<SharingChange> share = new ArrayList<>(super.getInitialShare(session));
    share.add(
        buildShare(
            session,
            AgentSession.principal(session.getAgentId(), session.getId()).toString(),
            StandardRole.EDITOR));
    return share;
  }

  private static boolean isCreatedWithinParentOrItself(final AgentSession session) {
    final Context context = Context.require();
    return context.isSystem()
        || context
            .principal()
            .map(
                creator ->
                    creator.actsIn(AssetClass.AGENT_SESSION, session.getParentSessionId())
                        || creator.actsIn(AssetClass.AGENT_SESSION, session.getId()))
            .orElse(false);
  }
}
