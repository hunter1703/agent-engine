package com.agentengine.tenancy;

import com.agentengine.tenancy.beans.User;
import com.agentengine.util.ms.client.MicroService;

@MicroService("tenancy")
public interface UserService extends AssetPermissionService {

  User get(String id);

  boolean isActive(String userId);

  /** The active user {@code password} belongs to, or null when it matches none. */
  User authenticate(String username, String password);

  User create(User user);

  User update(String id, User user);

  void delete(String id);
}
