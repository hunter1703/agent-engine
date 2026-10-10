package com.agentengine.tenancy;

import com.agentengine.tenancy.beans.User;
import com.agentengine.util.ms.client.MicroService;
import java.util.List;

@MicroService("tenancy")
public interface UserService {

  User get(String id);

  /** The user with only {@code includeFields} loaded, besides its id. */
  User get(String id, List<String> includeFields);

  boolean isActive(String userId);

  /** The active user {@code password} belongs to, or null when it matches none. */
  User authenticate(String username, String password);

  User create(User user);

  User update(String id, User user);

  void delete(String id);
}
