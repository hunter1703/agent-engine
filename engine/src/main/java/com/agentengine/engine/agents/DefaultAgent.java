package com.agentengine.engine.agents;

import com.agentengine.engine.api.LLMModel;

/**
 * A default agent implementation that uses a single model.
 * This serves as the basic agent type when no specific agent type is specified.
 */
public final class DefaultAgent extends AbstractSingleModelAgent {

    public DefaultAgent(final String name, final String description, final LLMModel model) {
        super(name, description, model);
    }
}