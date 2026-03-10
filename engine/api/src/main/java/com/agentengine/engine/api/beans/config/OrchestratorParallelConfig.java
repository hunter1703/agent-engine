package com.agentengine.engine.api.beans.config;

import com.agentengine.util.builder.annotations.UiField;
import com.agentengine.util.builder.annotations.UiNumber;
import com.agentengine.util.builder.annotations.UiRule;
import com.agentengine.util.builder.annotations.UiRuleEffect;
import com.agentengine.util.builder.annotations.UiSelect;

public class OrchestratorParallelConfig {

  @UiField(label = "Aggregation Policy", order = 10)
  @UiSelect
  private ParallelAggregationPolicy aggregationPolicy = ParallelAggregationPolicy.CONCATENATE;

  @UiField(label = "Stopping Policy", order = 20)
  @UiSelect
  private ParallelStoppingPolicy stoppingPolicy = ParallelStoppingPolicy.ALL_COMPLETE;

  @UiField(label = "Quorum", order = 30)
  @UiNumber
  @UiRule(effect = UiRuleEffect.VISIBLE, field = "stoppingPolicy", values = {"QUORUM"})
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
