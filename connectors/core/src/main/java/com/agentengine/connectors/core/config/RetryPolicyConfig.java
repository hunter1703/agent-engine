package com.agentengine.connectors.core.config;

import java.util.List;

public record RetryPolicyConfig(
        boolean enabled,
        int maxAttempts,
        long initialDelayMs,
        long maxDelayMs,
        double backoffMultiplier,
        String backoffType,
        List<Integer> retryableStatusCodes) {

    public RetryPolicyConfig {
        maxAttempts = maxAttempts <= 0 ? 1 : maxAttempts;
        initialDelayMs = Math.max(0L, initialDelayMs);
        maxDelayMs = Math.max(initialDelayMs, maxDelayMs);
        backoffMultiplier = backoffMultiplier <= 0 ? 1.0 : backoffMultiplier;
        backoffType = backoffType == null || backoffType.isBlank() ? RetryBackoffType.UNKNOWN.name() : backoffType;
        retryableStatusCodes =
                retryableStatusCodes == null ? List.of(429, 500, 502, 503, 504) : List.copyOf(retryableStatusCodes);
    }

    public RetryPolicyConfig(
            final boolean enabled,
            final int maxAttempts,
            final long initialDelayMs,
            final long maxDelayMs,
            final double backoffMultiplier,
            final RetryBackoffType backoffType,
            final List<Integer> retryableStatusCodes) {
        this(
                enabled,
                maxAttempts,
                initialDelayMs,
                maxDelayMs,
                backoffMultiplier,
                backoffType == null ? null : backoffType.name(),
                retryableStatusCodes);
    }

    public RetryBackoffType backoffTypeEnum() {
        return RetryBackoffType.valueOfOrDefault(backoffType);
    }

    public static RetryPolicyConfig disabled() {
        return new RetryPolicyConfig(false, 1, 0L, 0L, 1.0, RetryBackoffType.FIXED, List.of());
    }
}
