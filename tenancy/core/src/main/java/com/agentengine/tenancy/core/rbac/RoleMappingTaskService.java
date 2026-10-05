package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.core.repository.RoleMappingRepository;
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
 * The role mappings on single assets, as tasks partitioned by asset: handling an asset's pending
 * mappings recalculates its access list from all its mappings. A mapping left with no role is
 * deleted once done rather than kept.
 */
@Singleton
public class RoleMappingTaskService extends AbstractActorTaskService<RoleMapping> {

  private static final Logger LOG = LoggerFactory.getLogger(RoleMappingTaskService.class);
  private static final Duration STALE_AFTER = Duration.ofMinutes(1);

  private final RoleMappingRepository roleMappingRepository;
  private final AclCalculator aclCalculator;

  @Inject
  public RoleMappingTaskService(
      final RoleMappingRepository roleMappingRepository,
      final AclCalculator aclCalculator,
      final ActorSystemProvider actorSystemProvider) {
    super(actorSystemProvider);
    this.roleMappingRepository = roleMappingRepository;
    this.aclCalculator = aclCalculator;
  }

  @Override
  public String taskType() {
    return RoleMapping.TASK_TYPE;
  }

  @Override
  public Duration staleAfter() {
    return STALE_AFTER;
  }

  @Override
  public void handle(final List<RoleMapping> mappings) {
    final RoleMapping mapping = mappings.getFirst();
    aclCalculator.calculateAndUpdateGrants(mapping.getAssetClass(), mapping.getAssetId());
  }

  @Override
  public void markDone(final RoleMapping mapping) {
    if (mapping.getRoleIds().isEmpty()) {
      if (!roleMappingRepository.delete(mapping)) {
        LOG.info("Role mapping {} changed while processed; left pending", mapping.getId());
      }
      return;
    }
    try {
      roleMappingRepository.updateStatus(mapping, TaskStatus.DONE);
    } catch (final StaleStateException exception) {
      LOG.info("Role mapping {} changed while processed; left pending", mapping.getId());
    }
  }

  @Override
  public Collection<RoleMapping> findStale(final long pendingSince) {
    return roleMappingRepository.findWithStatus(TaskStatus.PENDING, pendingSince);
  }
}
