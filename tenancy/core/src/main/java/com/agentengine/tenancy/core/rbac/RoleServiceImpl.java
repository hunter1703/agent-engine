package com.agentengine.tenancy.core.rbac;

import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.tenancy.core.repository.RoleRepository;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@Singleton
public class RoleServiceImpl implements RoleService {

  public static final String ROLE_CACHE_NAME = "ROLE_CACHE";

  private final RoleRepository roleRepository;
  private final RoleTaskService roleTaskService;
  private final DistributedCache<Role> roleCache;

  @Inject
  public RoleServiceImpl(
      final RoleRepository roleRepository,
      final RoleTaskService roleTaskService,
      final DistributedCacheManager distributedCacheManager) {
    this.roleRepository = roleRepository;
    this.roleTaskService = roleTaskService;
    this.roleCache =
        new DistributedCache.Builder<Role>(ROLE_CACHE_NAME, distributedCacheManager)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(roleRepository::findById)
            .build();
  }

  @Override
  public Role createRole(final Role role) {
    return roleRepository.insert(role);
  }

  @Override
  public Role getRole(final String id) {
    return roleCache.get(id);
  }

  @Override
  public Map<String, Role> getRoles(final Collection<String> ids) {
    return CollectionUtils.isEmpty(ids)
        ? Map.of()
        : roleCache.getAll(ids, roleRepository::findByIds);
  }

  @Override
  public Role updateRole(final String id, final Role role) {
    roleCache.put(id, roleRepository.update(id, role));
    return role;
  }

  @Override
  public void deleteRole(final String id) {
    roleRepository.deleteByIdIgnoringVersion(id);
    roleCache.remove(id);
  }

  @Override
  public void resubmitStaleRoles() {
    roleTaskService
        .findStale(System.currentTimeMillis() - roleTaskService.staleAfter().toMillis())
        .forEach(roleTaskService::submit);
  }
}
