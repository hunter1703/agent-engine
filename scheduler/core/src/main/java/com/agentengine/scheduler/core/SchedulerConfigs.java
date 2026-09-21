package com.agentengine.scheduler.core;

import com.agentengine.util.common.config.ApplicationConfig;
import java.time.Duration;

public final class SchedulerConfigs {
  private static final String SCAN_INTERVAL_KEY = "agent-engine.scheduler.scan-interval-millis";
  private static final String RECONCILE_IN_FLIGHT_INTERVAL_KEY =
      "agent-engine.scheduler.reconcile-in-flight-interval-millis";
  private static final String POLL_INTERVAL_KEY = "agent-engine.scheduler.poll-interval-millis";
  private static final String MAX_TRIGGERS_PER_SCAN_KEY =
      "agent-engine.scheduler.max-triggers-per-scan";
  private static final String MAX_CONCURRENT_JOBS_KEY =
      "agent-engine.scheduler.max-concurrent-jobs";
  private static final String WORK_REQUEST_TIMEOUT_KEY =
      "agent-engine.scheduler.work-request-timeout-millis";
  private static final String HEARTBEAT_INTERVAL_KEY =
      "agent-engine.scheduler.heartbeat-interval-millis";
  private static final String ALLOWED_HEARTBEAT_MISSES_KEY =
      "agent-engine.scheduler.allowed-heartbeat-misses";

  private static final long DEFAULT_SCAN_INTERVAL_MILLIS = Duration.ofSeconds(60).toMillis();
  private static final long DEFAULT_RECONCILE_IN_FLIGHT_INTERVAL_MILLIS =
      Duration.ofMinutes(1).toMillis();
  private static final long DEFAULT_POLL_INTERVAL_MILLIS = Duration.ofSeconds(1).toMillis();
  private static final int DEFAULT_MAX_TRIGGERS_PER_SCAN = 10_000;
  private static final int DEFAULT_MAX_CONCURRENT_JOBS = 20;
  private static final long DEFAULT_WORK_REQUEST_TIMEOUT_MILLIS = Duration.ofSeconds(10).toMillis();
  private static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = Duration.ofSeconds(20).toMillis();
  private static final int DEFAULT_ALLOWED_HEARTBEAT_MISSES = 3;

  /** How often to look for due triggers; the floor on how soon a due trigger can be handed out. */
  private final Duration scanInterval;

  private final Duration reconcileInterval;

  /** How often a runner with free slots asks the scheduler for work. */
  private final Duration pollInterval;

  /**
   * A safety valve, not a throughput target — how much is handed out is governed by what the
   * runners have room for, and a normal backlog is far below this. It bounds what a pathological
   * one can pull into the scheduler, which is a cluster singleton: running it out of memory stops
   * scheduling everywhere, and would do so again on restart while the backlog remains.
   */
  private final int maxTriggersPerScan;

  private final int maxConcurrentJobs;

  /**
   * How long a runner waits for the scheduler to answer a request for work. The answer is sent
   * before any work starts, so this covers delivery and taking the triggers, not the run.
   */
  private final Duration workRequestTimeout;

  private final Duration heartbeatInterval;
  private final int allowedHeartbeatMisses;

  public SchedulerConfigs(final ApplicationConfig applicationConfig) {
    this.scanInterval =
        Duration.ofMillis(
            applicationConfig.getLong(SCAN_INTERVAL_KEY, DEFAULT_SCAN_INTERVAL_MILLIS));
    this.reconcileInterval =
        Duration.ofMillis(
            applicationConfig.getLong(
                RECONCILE_IN_FLIGHT_INTERVAL_KEY, DEFAULT_RECONCILE_IN_FLIGHT_INTERVAL_MILLIS));
    this.pollInterval =
        Duration.ofMillis(
            applicationConfig.getLong(POLL_INTERVAL_KEY, DEFAULT_POLL_INTERVAL_MILLIS));
    this.maxTriggersPerScan =
        applicationConfig.getInt(MAX_TRIGGERS_PER_SCAN_KEY, DEFAULT_MAX_TRIGGERS_PER_SCAN);
    this.maxConcurrentJobs =
        applicationConfig.getInt(MAX_CONCURRENT_JOBS_KEY, DEFAULT_MAX_CONCURRENT_JOBS);
    this.workRequestTimeout =
        Duration.ofMillis(
            applicationConfig.getLong(
                WORK_REQUEST_TIMEOUT_KEY, DEFAULT_WORK_REQUEST_TIMEOUT_MILLIS));
    this.heartbeatInterval =
        Duration.ofMillis(
            applicationConfig.getLong(HEARTBEAT_INTERVAL_KEY, DEFAULT_HEARTBEAT_INTERVAL_MILLIS));
    this.allowedHeartbeatMisses =
        applicationConfig.getInt(ALLOWED_HEARTBEAT_MISSES_KEY, DEFAULT_ALLOWED_HEARTBEAT_MISSES);
  }

  public Duration scanInterval() {
    return scanInterval;
  }

  public Duration reconcileInterval() {
    return reconcileInterval;
  }

  public Duration pollInterval() {
    return pollInterval;
  }

  public int maxTriggersPerScan() {
    return maxTriggersPerScan;
  }

  public int maxConcurrentJobs() {
    return maxConcurrentJobs;
  }

  public Duration workRequestTimeout() {
    return workRequestTimeout;
  }

  public Duration heartbeatInterval() {
    return heartbeatInterval;
  }

  public int allowedHeartbeatMisses() {
    return allowedHeartbeatMisses;
  }
}
