package com.agentengine.tenancy.core.services;

import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.beans.User;
import com.agentengine.tenancy.core.repository.UserRepository;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.distributed.DistributedCacheManager;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class UserServiceImpl implements UserService {

  private final UserRepository userRepository;
  private final DistributedCacheManager cacheManager;

  @Inject
  public UserServiceImpl(
      final UserRepository userRepository, final DistributedCacheManager cacheManager) {
    this.userRepository = userRepository;
    this.cacheManager = cacheManager;
  }

  @Override
  public User get(final String id) {
    return userRepository.findById(id);
  }

  @Override
  public boolean isActive(final String userId) {
    final User user = userRepository.findById(userId);
    return user != null
        && User.UserStatus.valueOfOrDefault(user.getStatus()) == User.UserStatus.ACTIVE;
  }

  @Override
  public User authenticate(final String username, final String password) {
    final User user = userRepository.authenticate(username, password);
    return user != null
            && User.UserStatus.valueOfOrDefault(user.getStatus()) == User.UserStatus.ACTIVE
        ? user
        : null;
  }

  @Override
  public User create(final User user) {
    if (StringUtils.isBlank(user.getPassword())) {
      throw new IllegalArgumentException("A password is required");
    }
    return userRepository.insert(user);
  }

  @Override
  public User update(final String id, final User user) {
    final User updated = userRepository.update(id, user);
    if (updated != null) {
      updated.setPasswordHash(null);
      if (User.UserStatus.valueOfOrDefault(user.getStatus()) == User.UserStatus.DISABLED) {
        cacheManager.invalidate("ACTIVE_USERS", id);
      }
    }
    return updated;
  }

  @Override
  public void delete(final String id) {
    userRepository.deleteByIdIgnoringVersion(id);
    cacheManager.invalidate("ACTIVE_USERS", id);
  }
}
