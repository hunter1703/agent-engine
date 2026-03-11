package com.agentengine.connectors.core.config;

import java.util.List;

public record RetryPolicyConfig(
    boolean enabled,
    int maxAttempts,
    long initialDelayMs,
    long maxDelayMs,
    double backoffMultiplier,
    RetryBackoffType backoffType,
    List<Integer> retryableStatusCodes) {

  public RetryPolicyConfig {
    maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
    initialDelayMs = Math.max(0L, initialDelayMs);
    maxDelayMs = Math.max(initialDelayMs, maxDelayMs);
    backoffMultiplier = backoffMultiplier <= 0 ? 1.0 : backoffMultiplier;
    backoffType = backoffType == null ? RetryBackoffType.UNKNOWN : backoffType;
    retryableStatusCodes =
        retryableStatusCodes == null
            ? List.of(429, 500, 502, 503, 504)
            : List.copyOf(retryableStatusCodes);
  }

  public static RetryPolicyConfig disabled() {
    return new RetryPolicyConfig(false, 1, 0L, 0L, 1.0, RetryBackoffType.FIXED, List.of());
  }
}
