package com.agentengine.engine.builders.agent;

import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.models.BaseLlm;

public class LLMStoryAgentBuilder extends LLMAgentBuilder {
    private AbstractLLM routingModel;
    private int routingHistorySize;

    public LLMAgentBuilder withRoutingModel(AbstractLLM routingModel) {
        this.routingModel = routingModel;
        return this;
    }

    public LLMAgentBuilder withRoutingHistorySize(int routingHistorySize) {
        this.routingHistorySize = routingHistorySize;
        return this;
    }

    public AbstractLLM routingModel() {
        return routingModel;
    }

    public int routingHistorySize() {
        return routingHistorySize;
    }
}
