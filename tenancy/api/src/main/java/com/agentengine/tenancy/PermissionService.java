package com.agentengine.tenancy;

import com.agentengine.util.ms.client.MicroService;
import com.agentengine.util.tenancy.Permission;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

@MicroService("tenancy")
public interface PermissionService {
  Map<String, Map<String, Set<Permission>>> getAssetClassPermissions(Collection<String> principals);
}
