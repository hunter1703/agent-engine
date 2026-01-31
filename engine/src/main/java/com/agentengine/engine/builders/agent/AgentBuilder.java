package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.SimpleAgent;
import com.agentengine.engine.model.LangChain4JLLMModel;
import com.google.adk.agents.LlmAgent;
import com.google.errorprone.annotations.CanIgnoreReturnValue;

public class AgentBuilder extends LlmAgent.Builder {
    private String protocolInstructions;
    private String toolInstructions;
    private LangChain4JLLMModel model;

    public AgentBuilder protocolInstructions(final String protocolInstructions) {
        this.protocolInstructions = protocolInstructions;
        return this;
    }

    public AgentBuilder toolInstructions(final String toolInstructions) {
        this.toolInstructions = toolInstructions;
        return this;
    }

    public LlmAgent.Builder reWriteInstructions() {
        return super.instruction(STR."""
                # PROTOCOL YOU MUST FOLLOW
                \{protocolInstructions}

                ---

                # TOOLS
                \{toolInstructions}
                """);
    }

    public AgentBuilder model(final LangChain4JLLMModel model) {
        this.model = model;
        return this;
    }

    public LangChain4JLLMModel getModel() {
        return model;
    }

    @Override
    public SimpleAgent build() {
        return new SimpleAgent(this) {
        };
    }
}
