package com.agentengine.util.context;

import com.fasterxml.jackson.annotation.JsonIgnore;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.Supplier;

/**
 * The request being served: its id, the customer it belongs to, and who it acts as. The customer is
 * null only for a request whose customer is not known yet.
 */
public record Context(String requestId, String customerId, Caller caller) {

  /** The customer of the system that runs every customer: the environment itself. */
  public static final String SYSTEM_CUSTOMER_ID = "system";

  private static final ScopedValue<Context> SCOPE = ScopedValue.newInstance();

  public Context {
    Objects.requireNonNull(requestId, "requestId");
    Objects.requireNonNull(caller, "caller");
  }

  /** A new request acting as the system across every customer. */
  public static Context asSystemCustomer() {
    return new Context(newRequestId(), SYSTEM_CUSTOMER_ID, Caller.SYSTEM);
  }

  /** A new request acting as the system of one customer, with every permission within it. */
  public static Context asSystemUser(final String customerId) {
    return new Context(newRequestId(), customerId, Caller.SYSTEM);
  }

  public Context asSystemCaller() {
    return new Context(requestId, customerId, Caller.SYSTEM);
  }

  public Context as(final Caller caller) {
    return new Context(requestId, customerId, caller);
  }

  /** This request, acting in {@code principal} alone, for the same user. */
  public Context actingAs(final Principal principal) {
    return as(UserCaller.of(principal.forUser(requireUserCaller().userId())));
  }

  /**
   * This request, also acting in {@code principal} for the same user; unchanged for a caller that
   * is not a user.
   */
  public Context alsoActingAs(final Principal principal) {
    return caller instanceof UserCaller userCaller ? as(userCaller.alsoIn(principal)) : this;
  }

  /** The caller, when it is a user rather than the system or nobody. */
  public Optional<UserCaller> userCaller() {
    return caller instanceof UserCaller userCaller ? Optional.of(userCaller) : Optional.empty();
  }

  /** The principal the request runs in and is attributed to, when its caller is a user. */
  public Optional<Principal> principal() {
    return userCaller().map(UserCaller::primaryPrincipal);
  }

  @JsonIgnore
  public boolean isSystem() {
    return caller instanceof Caller.SystemCaller;
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

  public static Context require() {
    return current().orElseThrow(() -> new IllegalStateException("No context is bound"));
  }

  public static Optional<String> currentCustomerId() {
    return current().map(Context::customerId);
  }

  public static String requireCustomerId() {
    return currentCustomerId()
        .orElseThrow(() -> new IllegalStateException("No customer in the current context"));
  }

  public static Optional<Principal> currentPrincipal() {
    return current().flatMap(Context::principal);
  }

  public static Optional<String> currentUserId() {
    return current().flatMap(Context::userCaller).map(UserCaller::userId);
  }

  public static Runnable bindCurrent(final Runnable runnable) {
    return current().<Runnable>map(context -> () -> context.run(runnable)).orElse(runnable);
  }

  public static <T> Callable<T> bindCurrent(final Callable<T> callable) {
    return current().<Callable<T>>map(context -> () -> context.call(callable)).orElse(callable);
  }

  private UserCaller requireUserCaller() {
    return userCaller()
        .orElseThrow(() -> new IllegalStateException(caller + " is not a user acting"));
  }

  private static String newRequestId() {
    return UUID.randomUUID().toString();
  }
}
