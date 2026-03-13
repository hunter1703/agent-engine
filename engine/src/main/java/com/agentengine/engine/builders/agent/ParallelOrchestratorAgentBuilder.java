package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.ParallelOrchestratorAgent;
import com.agentengine.engine.api.beans.config.ParallelAggregationPolicy;
import com.agentengine.engine.api.beans.config.ParallelStoppingPolicy;
import com.agentengine.engine.plugin.Agent;

public class ParallelOrchestratorAgentBuilder extends Agent.Builder<ParallelOrchestratorAgentBuilder, ParallelOrchestratorAgent> {
  private ParallelAggregationPolicy aggregationPolicy;
  private ParallelStoppingPolicy stoppingPolicy;
  private int quorum = 1;

  public ParallelOrchestratorAgentBuilder aggregationPolicy(final ParallelAggregationPolicy aggregationPolicy) {
    this.aggregationPolicy = aggregationPolicy;
    return this;
  }

  public ParallelOrchestratorAgentBuilder stoppingPolicy(final ParallelStoppingPolicy stoppingPolicy) {
    this.stoppingPolicy = stoppingPolicy;
    return this;
  }

  public ParallelOrchestratorAgentBuilder quorum(final int quorum) {
    this.quorum = quorum;
    return this;
  }

  public ParallelAggregationPolicy aggregationPolicy() {
    return aggregationPolicy;
  }

  public ParallelStoppingPolicy stoppingPolicy() {
    return stoppingPolicy;
  }

  public int quorum() {
    return quorum;
  }

  @Override
  public ParallelOrchestratorAgent build() {
    return new ParallelOrchestratorAgent(this);
  }
}
