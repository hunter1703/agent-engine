package com.agentengine.runtime.agents;

import com.agentengine.runtime.agents.flow.BaseFlow;
import com.google.adk.agents.LlmAgent;
import com.google.adk.flows.llmflows.BaseLlmFlow;
import com.google.adk.tools.BaseTool;

public final class BaseAgent extends LlmAgent {

    public BaseAgent(final Builder builder) {
        super(builder);
    }

    @Override
    protected BaseLlmFlow determineLlmFlow() {
        return new BaseFlow(maxSteps().orElse(null), tools().blockingGet().stream().map(BaseTool::name).toList());
    }
}
