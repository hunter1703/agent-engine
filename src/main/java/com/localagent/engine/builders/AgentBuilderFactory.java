package com.localagent.engine.builders;

import com.localagent.engine.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;

@Singleton
public class AgentBuilderFactory {

    private final Map<String, AgentBuilder> agentNameVsBuilder;
    private final HybridAgentBuilder hybridAgentBuilder;

    @Inject
    public AgentBuilderFactory(Instance<AgentBuilder> allBuilders, HybridAgentBuilder hybridAgentBuilder) {
        agentNameVsBuilder = CollectionUtils.transformToMultiKeyMap(allBuilders.stream().toList(), AgentBuilder::agentNames, Function.identity());
        this.hybridAgentBuilder = hybridAgentBuilder;
    }

    public AgentBuilder getBuilder(final String agentName) {
        return agentNameVsBuilder.getOrDefault(agentName, hybridAgentBuilder);
    }
}
