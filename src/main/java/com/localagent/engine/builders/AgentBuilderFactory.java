package com.localagent.engine.builders;

import com.localagent.engine.AgentEngine;
import com.localagent.engine.utils.CollectionUtils;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class AgentBuilderFactory {

    private final Map<String, AgentBuilder> agentNameVsBuilder;
    private final DefaultAgentBuilder defaultAgentBuilder;

    @Inject
    public AgentBuilderFactory(Instance<AgentBuilder> allBuilders, DefaultAgentBuilder defaultAgentBuilder) {
        agentNameVsBuilder = CollectionUtils.transformToMap(allBuilders.stream().toList(), AgentBuilder::agentNames, Function.identity());
        this.defaultAgentBuilder = defaultAgentBuilder;
    }

    public AgentBuilder getBuilder(final String agentName) {
        return agentNameVsBuilder.getOrDefault(agentName, defaultAgentBuilder);
    }
}
