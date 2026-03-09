package com.agentengine.engine.api.beans.config;

/**
 * Parallel orchestration policies for orchestrator agents.
 *
 * <p>These settings are metadata-level runtime controls consumed by orchestrator execution mode.
 */
public class OrchestratorParallelConfig {
  private ParallelAggregationPolicy aggregationPolicy = ParallelAggregationPolicy.CONCATENATE;
  private ParallelStoppingPolicy stoppingPolicy = ParallelStoppingPolicy.ALL_COMPLETE;
  private int quorum = 1;

  public ParallelAggregationPolicy getAggregationPolicy() {
    return aggregationPolicy;
  }

  public void setAggregationPolicy(final ParallelAggregationPolicy aggregationPolicy) {
    this.aggregationPolicy =
        aggregationPolicy == null ? ParallelAggregationPolicy.CONCATENATE : aggregationPolicy;
  }

  public ParallelStoppingPolicy getStoppingPolicy() {
    return stoppingPolicy;
  }

  public void setStoppingPolicy(final ParallelStoppingPolicy stoppingPolicy) {
    this.stoppingPolicy =
        stoppingPolicy == null ? ParallelStoppingPolicy.ALL_COMPLETE : stoppingPolicy;
  }

  public int getQuorum() {
    return quorum;
  }

  public void setQuorum(final int quorum) {
    this.quorum = Math.max(1, quorum);
  }
}
