package com.agentengine.util.common.context;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

public record Context(String requestId, UserContext userContext) {

  private static final ScopedValue<Context> SCOPE = ScopedValue.newInstance();

  public Context {
    Objects.requireNonNull(requestId, "requestId");
  }

  public Context(final String requestId) {
    this(requestId, null);
  }

  public static Optional<Context> current() {
    return SCOPE.isBound() ? Optional.of(SCOPE.get()) : Optional.empty();
  }

  public String customerId() {
    return userContext == null ? null : userContext.customerId();
  }

  public String userId() {
    return userContext == null ? null : userContext.userId();
  }

  public void run(final Runnable runnable) {
    ScopedValue.where(SCOPE, this).run(runnable);
  }

  public <T> T call(final Callable<T> callable) throws Exception {
    return ScopedValue.where(SCOPE, this).call(callable::call);
  }
}
