package com.agentengine.engine.services;

import com.agentengine.engine.agents.AgentRunner;
import com.agentengine.engine.agents.AgentSessionRuntime;
import com.agentengine.engine.agents.AgentSessionRuntimeManager;
import com.agentengine.engine.api.AgentRequest;
import com.agentengine.engine.api.services.AgentExecutionService;
import com.google.adk.events.Event;
import io.reactivex.rxjava3.core.Flowable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class AgentExecutionServiceImpl implements AgentExecutionService {

    private final AgentSessionRuntimeManager agentSessionRuntimeManager;
    private final AgentRunner agentRunner;

    @Inject
    public AgentExecutionServiceImpl(AgentSessionRuntimeManager agentSessionRuntimeManager, AgentRunner agentRunner) {
        this.agentSessionRuntimeManager = agentSessionRuntimeManager;
        this.agentRunner = agentRunner;
    }

    @Override
    public Flowable<Event> run(AgentRequest request) {
        AgentSessionRuntime runtime = agentSessionRuntimeManager.getOrStartRuntime(request.getAgentId(),
                request.getSessionId());
        return agentRunner.run(runtime, request.getMessage());
    }

    @Override
    public Flowable<Event> runStreaming(AgentRequest request) {
        AgentSessionRuntime runtime = agentSessionRuntimeManager.getOrStartRuntime(request.getAgentId(),
                request.getSessionId());
        return agentRunner.runStreaming(runtime, request.getMessage());
    }
}
