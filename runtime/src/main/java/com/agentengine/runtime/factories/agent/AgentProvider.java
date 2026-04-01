package com.agentengine.runtime.factories.agent;

import com.agentengine.runtime.agents.Agent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.util.common.CollectionUtils;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentProvider {

    private final Map<String, AgentFactory<?, ?>> typeVsFactory;
    private final DefaultAgentFactory defaultAgentFactory;

    @Inject
    public AgentProvider(
            final @Any Instance<AgentFactory<?, ?>> allFactories, final DefaultAgentFactory defaultAgentFactory) {
        typeVsFactory =
                CollectionUtils.transformToMap(allFactories.stream().toList(), AgentFactory::type, Function.identity());
        this.defaultAgentFactory = defaultAgentFactory;
    }

    public <C extends BaseAgentConfig, A extends Agent> A create(final C config) {
        // noinspection unchecked
        final AgentFactory<C, A> factory =
                (AgentFactory<C, A>) typeVsFactory.getOrDefault(config.getType(), defaultAgentFactory);
        return factory.build(config);
    }
}
