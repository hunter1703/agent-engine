package com.agentengine.agent.infra.factories.agent;

import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.agent.infra.agents.DelegatedAgent;
import com.agentengine.agent.infra.factories.model.ModelProvider;
import com.agentengine.agent.infra.tools.ToolFactory;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Singleton;

@Singleton
@Named("defaultAgentFactory")
public class DefaultAgentFactory extends AbstractAgentFactory<BaseAgentConfig, Agent> {

    @Inject
    public DefaultAgentFactory(final ModelProvider modelProvider, final ToolFactory toolFactory) {
        super(modelProvider, toolFactory);
    }

    @Override
    public DelegatedAgent build(final BaseAgentConfig config) {
        return createLlmAgentBuilder(config).build();
    }

    @Override
    public String type() {
        return BaseAgentConfig.AgentType.DEFAULT.type();
    }
}
