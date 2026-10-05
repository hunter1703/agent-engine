package com.agentengine.tenancy.core.rbac;

import static com.agentengine.util.tenancy.AclService.PERMISSIONS_ON_EVERY_ASSET_CACHE;

import com.agentengine.tenancy.core.repository.RoleRepository;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.common.repository.EntityChangeListener;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.tasks.TaskStatus;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.List;

/**
 * Evicts the written roles from the role cache, on every node; when a write may have changed what a
 * role grants — it left the role pending, deleted it, or was by a filter — then drops the
 * customer's cached permissions on every asset, on every node; and submits every role it left
 * pending. The role cache is evicted first, so permissions reloaded meanwhile read the roles as
 * written. Reads the roles when the write did not carry them.
 */
@Singleton
public class RoleChangeListener implements EntityChangeListener<Role> {

  private final RoleRepository roleRepository;
  private final RoleTaskService roleTaskService;
  private final DistributedCacheManager distributedCacheManager;

  @Inject
  public RoleChangeListener(
      final RoleRepository roleRepository,
      final RoleTaskService roleTaskService,
      final DistributedCacheManager distributedCacheManager) {
    this.roleRepository = roleRepository;
    this.roleTaskService = roleTaskService;
    this.distributedCacheManager = distributedCacheManager;
  }

  @Override
  public Class<Role> entityClass() {
    return Role.class;
  }

  @Override
  public void onChange(final EntityChange<Role> change) {
    final Collection<Role> roles =
        switch (change) {
          case EntityChange.Entities<Role> changed -> {
            changed.idVsEntity().keySet().forEach(this::evictRole);
            yield changed.idVsEntity().values();
          }
          case EntityChange.Ids<Role> changed -> {
            changed.ids().forEach(this::evictRole);
            yield roleRepository.findByIds(changed.ids()).values();
          }
        };
    final List<Role> pending =
        roles.stream()
            .filter(role -> TaskStatus.valueOfOrDefault(role.getStatus()) == TaskStatus.PENDING)
            .toList();
    if (change.type() == EntityChange.Type.DELETED || !pending.isEmpty()) {
      distributedCacheManager.invalidateInCustomerScope(PERMISSIONS_ON_EVERY_ASSET_CACHE);
    }
    pending.forEach(roleTaskService::submit);
  }

  private void evictRole(final String roleId) {
    distributedCacheManager.invalidate(RoleServiceImpl.ROLE_CACHE_NAME, roleId);
  }
}
