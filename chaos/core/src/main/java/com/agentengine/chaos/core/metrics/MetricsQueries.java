package com.agentengine.chaos.core.metrics;

/**
 * PromQL queries used by {@link MetricsCollector}. Kept configurable rather than hardcoded so the
 * exact expressions can be tuned to whatever exporters (Micrometer, mongodb-exporter,
 * pekko-persistence metrics) are actually running in a given cluster without a code change.
 */
public record MetricsQueries(
    String successRateQuery,
    String p50LatencyQuery,
    String p95LatencyQuery,
    String p99LatencyQuery,
    String errorRateQuery,
    String podRestartsQuery,
    String eventJournalLagQuery,
    String mongoLatencyQuery) {

  public static MetricsQueries defaults() {
    return new MetricsQueries(
        "sum(rate(http_server_requests_seconds_count{outcome=\"SUCCESS\"}[1m]))"
            + " / sum(rate(http_server_requests_seconds_count[1m]))",
        "histogram_quantile(0.50, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))",
        "histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))",
        "histogram_quantile(0.99, sum(rate(http_server_requests_seconds_bucket[1m])) by (le))",
        "sum(rate(http_server_requests_seconds_count{outcome=\"SERVER_ERROR\"}[1m]))"
            + " / sum(rate(http_server_requests_seconds_count[1m]))",
        "sum(increase(kube_pod_container_status_restarts_total{namespace=\"agent-engine\"}[5m]))",
        "max(pekko_persistence_journal_write_duration_seconds)",
        "avg(mongodb_op_latency_seconds)");
  }
}
