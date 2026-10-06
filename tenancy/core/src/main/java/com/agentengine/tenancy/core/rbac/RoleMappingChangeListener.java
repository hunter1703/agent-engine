package com.agentengine.tenancy.core.rbac;

import static com.agentengine.util.tenancy.AclService.PERMISSIONS_ON_EVERY_ASSET_CACHE;

import com.agentengine.tenancy.core.repository.RoleMappingRepository;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tasks.TaskStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;

/**
 * Submits every role mapping on one asset a write left pending, and drops the cached permissions on
 * every asset of the principals of the mappings on every asset it changed, on every node. Reads the
 * mappings, by their ids or by the write's filter, when the write did not carry them; a mapping
 * deleted for holding no role was already seen by the write that emptied it.
 */
@Singleton
public class RoleMappingChangeListener implements EntityChangeListener<RoleMapping> {

  private final RoleMappingRepository roleMappingRepository;
  private final RoleMappingTaskService roleMappingTaskService;
  private final DistributedCacheManager distributedCacheManager;

  @Inject
  public RoleMappingChangeListener(
      final RoleMappingRepository roleMappingRepository,
      final RoleMappingTaskService roleMappingTaskService,
      final DistributedCacheManager distributedCacheManager) {
    this.roleMappingRepository = roleMappingRepository;
    this.roleMappingTaskService = roleMappingTaskService;
    this.distributedCacheManager = distributedCacheManager;
  }

  @Override
  public Class<RoleMapping> entityClass() {
    return RoleMapping.class;
  }

  @Override
  public void onChange(final EntityChange<RoleMapping> change) {
    boolean updateGrantsInSync;
    final Collection<RoleMapping> mappings =
        switch (change) {
          case EntityChange.Entities<RoleMapping> changed -> {
            updateGrantsInSync =
                Boolean.TRUE.equals(
                    CollectionUtils.getBooleanValueFromMap(
                        changed.additional(), RoleMappingRepository.UPDATE_GRANTS_IN_SYNC));
            yield changed.idVsEntity().values();
          }
          case EntityChange.Ids<RoleMapping> changed -> {
            updateGrantsInSync =
                Boolean.TRUE.equals(
                    CollectionUtils.getBooleanValueFromMap(
                        changed.additional(), RoleMappingRepository.UPDATE_GRANTS_IN_SYNC));
            yield roleMappingRepository.findByIds(changed.ids()).values();
          }
        };
    for (final RoleMapping mapping : mappings) {
      if (mapping.partitionId() == null) {
        distributedCacheManager.broadcastInvalidation(
            PERMISSIONS_ON_EVERY_ASSET_CACHE,
            CacheScope.CUSTOMER.namespace(
                PERMISSIONS_ON_EVERY_ASSET_CACHE, mapping.getPrincipal()));
      } else if (TaskStatus.valueOfOrDefault(mapping.getStatus()) == TaskStatus.PENDING) {
        if (updateGrantsInSync) {
          roleMappingTaskService.handle(List.of(mapping));
          roleMappingTaskService.markDone(mapping);
        } else {
          roleMappingTaskService.submit(mapping);
        }
      }
    }
  }
}
