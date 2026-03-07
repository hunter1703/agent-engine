package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.agents.story.StoryAgent;
import com.agentengine.engine.model.AbstractLLM;
import com.google.adk.models.BaseLlm;

public class LLMStoryAgentBuilder extends LLMAgentBuilder {
    private AbstractLLM routingModel;
    private int routingHistorySize;

    public LLMStoryAgentBuilder withRoutingModel(AbstractLLM routingModel) {
        this.routingModel = routingModel;
        return this;
    }

    public LLMStoryAgentBuilder withRoutingHistorySize(int routingHistorySize) {
        this.routingHistorySize = routingHistorySize;
        return this;
    }

    public AbstractLLM routingModel() {
        return routingModel;
    }

    public int routingHistorySize() {
        return routingHistorySize;
    }

    @Override
    public StoryAgent build() {
        validate();
        return new StoryAgent(this);
    }
}
