package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.tenancy.RoleService;
import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.ms.client.MicroServiceClientProvider;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;

@Singleton
public class RoleAssetHandler extends NamedAssetHandler<Role> {

  private final RoleService roleService;

  @Inject
  public RoleAssetHandler(final MicroServiceClientProvider provider) {
    this.roleService = provider.getRaw(RoleService.class);
  }

  @Override
  public String getAssetType() {
    return AssetClass.ROLE;
  }

  @Override
  public PaginatedResult<Role> findAssets(final AssetRequest request) {
    return roleService.findRoles(request.getQuery());
  }

  @Override
  public Map<String, Role> getAssetsByIds(final AssetRequest request) {
    if (CollectionUtils.isEmpty(request.getKeys())) {
      return Map.of();
    }
    return roleService.getRoles(request.getKeys());
  }

  @Override
  protected String getName(final Role role) {
    return role.getName();
  }
}
