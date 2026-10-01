package com.agentengine.identity;

import java.time.Duration;
import java.util.Optional;

public interface IdentityProvider {

  Duration SESSION_LIFETIME = Duration.ofDays(7);

  LoginResult login(LoginRequest request);

  /** The live login session {@code token} was issued for, of a user still active. */
  Optional<UserSession> authenticate(String token);

  void logout(String token);
}
