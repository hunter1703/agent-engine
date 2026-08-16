package com.agentengine.chaos.api;

import java.time.Duration;
import java.time.Instant;

public record SteadyStateMetrics(
    double successRate,
    Duration p50Latency,
    Duration p95Latency,
    Duration p99Latency,
    double errorRate,
    int activeSessions,
    Duration eventJournalLag,
    Duration mongoLatency,
    int podRestarts,
    Instant timestamp) {}
