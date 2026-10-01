package com.agentengine.identity;

import com.agentengine.tenancy.UserService;
import com.agentengine.tenancy.beans.User;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.utils.HashUtils;
import com.agentengine.util.context.Context;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Singleton
public class PasswordIdentityProvider implements IdentityProvider {

  private static final String SESSION_CACHE_NAME = "USER_SESSION";

  private static final long SESSION_CACHE_TTL_SECONDS = 300;
  private static final long SESSION_CACHE_MAX_SIZE = 100_000;

  private final UserService userService;
  private final UserSessionRepository userSessionRepository;
  private final DistributedCache<UserSession> sessionCache;

  @Inject
  public PasswordIdentityProvider(
      final UserService userService,
      final UserSessionRepository userSessionRepository,
      final DistributedCacheManager cacheManager) {
    this.userService = userService;
    this.userSessionRepository = userSessionRepository;
    this.sessionCache =
        new DistributedCache.Builder<UserSession>(SESSION_CACHE_NAME, cacheManager)
            .scope(CacheScope.GLOBAL)
            .localCache(
                CacheBuilder.newBuilder()
                    .maximumSize(SESSION_CACHE_MAX_SIZE)
                    .expireAfterWrite(SESSION_CACHE_TTL_SECONDS, TimeUnit.SECONDS))
            .loader(this::loadSession)
            .build();
  }

  @Override
  public LoginResult login(final LoginRequest request) {
    if (!(request instanceof PasswordLoginRequest passwordRequest)) {
      throw new UnsupportedOperationException(
          "Unsupported login request type: " + request.getClass().getSimpleName());
    }
    final String customerId = Context.requireCustomerId();
    final User user =
        Context.asSystemUser(customerId)
            .get(
                () ->
                    userService.authenticate(
                        passwordRequest.getUsername(), passwordRequest.getPassword()));
    if (user == null) {
      throw new UnauthorizedException("Invalid username or password");
    }
    final String token = UUID.randomUUID().toString();
    final UserSession session = new UserSession();
    session.setId(tokenHash(token));
    session.setCustomerId(customerId);
    session.setUserId(user.getId());
    session.setExpiresAt(Date.from(Instant.now().plus(SESSION_LIFETIME)));
    userSessionRepository.insert(session);
    return new LoginResult(token);
  }

  @Override
  public Optional<UserSession> authenticate(final String token) {
    if (!isWellFormed(token)) {
      return Optional.empty();
    }
    return Optional.ofNullable(sessionCache.get(tokenHash(token)));
  }

  @Override
  public void logout(final String token) {
    if (!isWellFormed(token)) {
      return;
    }
    final String tokenHash = tokenHash(token);
    userSessionRepository.deleteByIdIgnoringVersion(tokenHash);
    sessionCache.invalidate(tokenHash);
  }

  private UserSession loadSession(final String tokenHash) {
    final UserSession session = userSessionRepository.findById(tokenHash);
    if (session == null || session.getExpiresAt().before(new Date())) {
      return null;
    }
    final boolean active =
        Context.asSystemUser(session.getCustomerId())
            .get(() -> userService.isActive(session.getUserId()));
    return active ? session : null;
  }

  private static String tokenHash(final String token) {
    return HashUtils.sha256Hex(token);
  }

  private static boolean isWellFormed(final String token) {
    if (token == null) {
      return false;
    }
    try {
      UUID.fromString(token);
      return true;
    } catch (final IllegalArgumentException exception) {
      return false;
    }
  }
}
