package com.agentengine.engine.builders.agent;

import com.agentengine.engine.agents.DefaultAgent;
import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.LLMModel;
import com.agentengine.engine.api.beans.config.AgentConfig;
import com.agentengine.engine.builders.model.ModelProvider;
import com.agentengine.engine.builders.sessionstore.SessionStoreProvider;
import jakarta.enterprise.inject.Default;
import jakarta.inject.Singleton;

@Singleton
@Default
public final class DefaultAgentBuilder extends AbstractAgentBuilder<AgentConfig, DefaultAgent> {

    public DefaultAgentBuilder(ModelProvider modelProvider, SessionStoreProvider sessionStoreProvider) {
        super(modelProvider, sessionStoreProvider);
    }

    @Override
    public DefaultAgent build(AgentConfig config) {
        final LLMModel model = modelProvider.get(config.getName(), config.getModel(), null);
        return new DefaultAgent(config.getName(), "Default Agent - single model agent", model);
    }

    @Override
    public String type() {
        return AgentConfig.AgentType.DEFAULT.name().toLowerCase();
    }
}