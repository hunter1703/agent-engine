package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.core.repository.RoleMappingRepository;
import com.agentengine.tenancy.core.repository.RoleRepository;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.tasks.AbstractActorTaskService;
import com.agentengine.util.tasks.TaskStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The roles, as tasks each of its own partition: handling an edited role marks every role mapping
 * on one asset that uses it pending again.
 */
@Singleton
public class RoleTaskService extends AbstractActorTaskService<Role> {

  private static final Logger LOG = LoggerFactory.getLogger(RoleTaskService.class);
  // Longer, since a role may be mapped onto many assets.
  private static final Duration STALE_AFTER = Duration.ofMinutes(5);

  private final RoleRepository roleRepository;
  private final RoleMappingRepository roleMappingRepository;

  @Inject
  public RoleTaskService(
      final RoleRepository roleRepository,
      final RoleMappingRepository roleMappingRepository,
      final ActorSystemProvider actorSystemProvider) {
    super(actorSystemProvider);
    this.roleRepository = roleRepository;
    this.roleMappingRepository = roleMappingRepository;
  }

  @Override
  public String taskType() {
    return Role.TASK_TYPE;
  }

  @Override
  public Duration staleAfter() {
    return STALE_AFTER;
  }

  @Override
  public void handle(final List<Role> roles) {
    for (final Role role : roles) {
      roleMappingRepository.updateStatusOnAssetsWithRole(role.getId(), TaskStatus.PENDING);
    }
  }

  @Override
  public void markDone(final Role role) {
    try {
      roleRepository.updateStatus(role, TaskStatus.DONE);
    } catch (final StaleStateException exception) {
      LOG.info("Role {} changed while processed; left pending", role.getId());
    }
  }

  @Override
  public Collection<Role> findStale(final long pendingSince) {
    return roleRepository.findWithStatus(TaskStatus.PENDING, pendingSince);
  }
}
