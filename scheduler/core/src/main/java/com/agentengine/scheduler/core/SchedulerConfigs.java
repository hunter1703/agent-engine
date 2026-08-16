package com.agentengine.scheduler.core;

import com.agentengine.util.common.config.ApplicationConfig;
import java.time.Duration;

/**
 * How often the scheduler looks for work, how much it will take at once, and how long it waits for
 * a runner to answer.
 *
 * @param scanInterval how often to look for due triggers; the floor on dispatch latency
 * @param maxTriggersPerScan a safety valve, not a throughput target — how much is dispatched is
 *     governed by the tag quotas, and a normal backlog is far below this. It bounds what a
 *     pathological one can pull into the scheduler, which is a cluster singleton: running it out of
 *     memory stops scheduling everywhere, and would do so again on restart while the backlog
 *     remains.
 * @param dispatchAskTimeout how long to wait for a runner to say whether it took a trigger. The
 *     answer is sent before any work starts, so this covers delivery and a permit check, not the
 *     run.
 */
public record SchedulerConfigs(
    Duration scanInterval,
    int maxTriggersPerScan,
    Duration dispatchAskTimeout,
    Duration heartbeatInterval,
    int allowedHeartbeatMisses) {

  private static final String SCAN_INTERVAL_KEY = "agent-engine.scheduler.scan-interval-millis";
  private static final String MAX_TRIGGERS_PER_SCAN_KEY =
      "agent-engine.scheduler.max-triggers-per-scan";
  private static final String DISPATCH_ASK_TIMEOUT_KEY =
      "agent-engine.scheduler.dispatch-ask-timeout-millis";
  private static final String HEARTBEAT_INTERVAL_KEY =
      "agent-engine.scheduler.heartbeat-interval-millis";
  private static final String ALLOWED_HEARTBEAT_MISSES_KEY =
      "agent-engine.scheduler.allowed-heartbeat-misses";

  private static final long DEFAULT_SCAN_INTERVAL_MILLIS = Duration.ofSeconds(5).toMillis();
  private static final int DEFAULT_MAX_TRIGGERS_PER_SCAN = 10_000;
  private static final long DEFAULT_DISPATCH_ASK_TIMEOUT_MILLIS = Duration.ofSeconds(10).toMillis();
  private static final long DEFAULT_HEARTBEAT_INTERVAL_MILLIS = Duration.ofSeconds(20).toMillis();
  private static final int DEFAULT_ALLOWED_HEARTBEAT_MISSES = 3;

  public static SchedulerConfigs from(final ApplicationConfig applicationConfig) {
    return new SchedulerConfigs(
        Duration.ofMillis(
            applicationConfig.getLong(SCAN_INTERVAL_KEY, DEFAULT_SCAN_INTERVAL_MILLIS)),
        applicationConfig.getInt(MAX_TRIGGERS_PER_SCAN_KEY, DEFAULT_MAX_TRIGGERS_PER_SCAN),
        Duration.ofMillis(
            applicationConfig.getLong(
                DISPATCH_ASK_TIMEOUT_KEY, DEFAULT_DISPATCH_ASK_TIMEOUT_MILLIS)),
        Duration.ofMillis(
            applicationConfig.getLong(HEARTBEAT_INTERVAL_KEY, DEFAULT_HEARTBEAT_INTERVAL_MILLIS)),
        applicationConfig.getInt(ALLOWED_HEARTBEAT_MISSES_KEY, DEFAULT_ALLOWED_HEARTBEAT_MISSES));
  }
}
