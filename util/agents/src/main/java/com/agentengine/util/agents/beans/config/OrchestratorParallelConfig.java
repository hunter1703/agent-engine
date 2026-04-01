package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.builder.annotations.UiField;
import com.agentengine.util.common.builder.annotations.UiNumber;
import com.agentengine.util.common.builder.annotations.UiRule;
import com.agentengine.util.common.builder.annotations.UiRuleEffect;
import com.agentengine.util.common.builder.annotations.UiSelect;

public class OrchestratorParallelConfig {
    private static final String DEFAULT_AGGREGATION_POLICY = ParallelAggregationPolicy.CONCATENATE.name();
    private static final String DEFAULT_STOPPING_POLICY = ParallelStoppingPolicy.ALL_COMPLETE.name();

    @UiField(label = "Aggregation Policy", order = 10)
    @UiSelect(enumType = ParallelAggregationPolicy.class)
    private String aggregationPolicy = DEFAULT_AGGREGATION_POLICY;

    @UiField(label = "Stopping Policy", order = 20)
    @UiSelect(enumType = ParallelStoppingPolicy.class)
    private String stoppingPolicy = DEFAULT_STOPPING_POLICY;

    @UiField(label = "Quorum", order = 30)
    @UiNumber
    @UiRule(
            effect = UiRuleEffect.VISIBLE,
            field = "stoppingPolicy",
            values = {"QUORUM"})
    private int quorum = 1;

    public String getAggregationPolicy() {
        return aggregationPolicy;
    }

    public void setAggregationPolicy(final String aggregationPolicy) {
        this.aggregationPolicy = aggregationPolicy == null || aggregationPolicy.isBlank()
                ? DEFAULT_AGGREGATION_POLICY
                : aggregationPolicy;
    }

    public ParallelAggregationPolicy aggregationPolicyEnum() {
        return ParallelAggregationPolicy.valueOfOrDefault(aggregationPolicy);
    }

    public String getStoppingPolicy() {
        return stoppingPolicy;
    }

    public void setStoppingPolicy(final String stoppingPolicy) {
        this.stoppingPolicy =
                stoppingPolicy == null || stoppingPolicy.isBlank() ? DEFAULT_STOPPING_POLICY : stoppingPolicy;
    }

    public ParallelStoppingPolicy stoppingPolicyEnum() {
        return ParallelStoppingPolicy.valueOfOrDefault(stoppingPolicy);
    }

    public int getQuorum() {
        return quorum;
    }

    public void setQuorum(final int quorum) {
        this.quorum = Math.max(1, quorum);
    }
}
