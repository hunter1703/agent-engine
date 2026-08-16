package com.agentengine.chaos.core.injection;

import java.time.Duration;
import java.util.function.BooleanSupplier;

/**
 * Polls a caller-supplied health check after a database fault is removed, confirming reconnection
 * happens within a bounded window rather than assuming Toxiproxy removal is instantaneous for the
 * downstream connection pool.
 */
public final class DatabaseReconnectionValidator {

  private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);
  private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

  private DatabaseReconnectionValidator() {}

  /**
   * Polls {@code healthCheck} every {@link #POLL_INTERVAL} until it succeeds or 30 seconds elapse.
   */
  public static boolean verifyReconnection(final BooleanSupplier healthCheck) {
    return verifyReconnection(healthCheck, DEFAULT_TIMEOUT);
  }

  public static boolean verifyReconnection(
      final BooleanSupplier healthCheck, final Duration timeout) {
    final long deadline = System.nanoTime() + timeout.toNanos();
    while (System.nanoTime() < deadline) {
      if (healthCheck.getAsBoolean()) {
        return true;
      }
      sleep(POLL_INTERVAL);
    }
    return healthCheck.getAsBoolean();
  }

  private static void sleep(final Duration duration) {
    try {
      Thread.sleep(duration.toMillis());
    } catch (final InterruptedException ex) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while polling for database reconnection", ex);
    }
  }
}
