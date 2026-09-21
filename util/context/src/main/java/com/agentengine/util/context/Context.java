package com.agentengine.util.context;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

public record Context(String requestId, UserContext userContext) {

  private static final ScopedValue<Context> SCOPE = ScopedValue.newInstance();

  public Context {
    Objects.requireNonNull(requestId, "requestId");
  }

  public Context(final String requestId) {
    this(requestId, null);
  }

  public void run(final Runnable runnable) {
    ScopedValue.where(SCOPE, this).run(runnable);
  }

  public <T> T call(final Callable<T> callable) throws Exception {
    return ScopedValue.where(SCOPE, this).call(callable::call);
  }

  public <T> T get(final Supplier<T> supplier) {
    return ScopedValue.where(SCOPE, this).call(supplier::get);
  }

  public static Optional<Context> current() {
    return SCOPE.isBound() ? Optional.of(SCOPE.get()) : Optional.empty();
  }

  public static Optional<UserContext> getUserContext() {
    return current().map(Context::userContext);
  }

  public static Optional<Integer> customerId() {
    return getUserContext().map(UserContext::customerId);
  }

  public static int requireCustomerId() {
    return customerId().orElseThrow(() -> new IllegalStateException("No customer in the current context"));
  }

  public static Optional<Integer> userId() {
    return getUserContext().map(UserContext::userId);
  }

  public static Runnable bindCurrent(final Runnable runnable) {
    return current().<Runnable>map(context -> () -> context.run(runnable)).orElse(runnable);
  }

  public static <T> Callable<T> bindCurrent(final Callable<T> callable) {
    return current().<Callable<T>>map(context -> () -> context.call(callable)).orElse(callable);
  }
}
