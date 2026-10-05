package com.agentengine.tenancy;

import com.agentengine.tenancy.beans.Role;
import com.agentengine.util.ms.client.MicroService;

import java.util.Collection;
import java.util.Map;

/**
 * The role management service.
 */
@MicroService("tenancy")
public interface RoleService {

  Role createRole(Role role);

  Role getRole(String id);

  Map<String, Role> getRoles(Collection<String> ids);

  Role updateRole(String id, Role role);

  void deleteRole(String id);

  /** Submits again every role left pending for too long. */
  void resubmitStaleRoles();
}
