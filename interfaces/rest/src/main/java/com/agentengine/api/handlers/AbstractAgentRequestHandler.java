package com.agentengine.api.handlers;

import com.agentengine.client.AgentRequest;
import com.agentengine.engine.AgentEngine;
import com.agentengine.interfaces.AgentService;

public abstract class AbstractAgentRequestHandler implements AgentRequestHandler {

    private final AgentService agentService;

    public AbstractAgentRequestHandler(AgentService agentService) {
        this.agentService = agentService;
    }

    protected AgentEngine getOrCreateEngine(final AgentRequest request) {
        return agentService.getOrStartEngine(request.getAgentName(), request.getAgentConfigPath());
    }
}
