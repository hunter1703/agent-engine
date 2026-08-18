package com.agentengine.util.common.context;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;

/**
 * Ambient context carried for the lifetime of a single request, from the point it enters the system
 * (REST, scheduler trigger, etc.) through every downstream microservice call, so that logging and
 * diagnostics can correlate work back to the originating request.
 *
 * <p>This is the top-level, request-scoped context. Concerns that are scoped narrower than a
 * request but wider than a single call (e.g. the authenticated user or tenant once multi-tenancy
 * lands) should nest inside this type rather than replace it, since a request always has exactly
 * one requestId but may carry zero or more identities within it.
 */
public record Context(String requestId) {

  private static final ScopedValue<Context> SCOPE = ScopedValue.newInstance();

  public Context {
    Objects.requireNonNull(requestId, "requestId");
  }

  public static Optional<Context> current() {
    return SCOPE.isBound() ? Optional.of(SCOPE.get()) : Optional.empty();
  }

  public void run(final Runnable runnable) {
    ScopedValue.where(SCOPE, this).run(runnable);
  }

  public <T> T call(final Callable<T> callable) throws Exception {
    return ScopedValue.where(SCOPE, this).call(callable::call);
  }
}
