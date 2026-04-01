package com.agentengine.runtime.factories.agent;

import com.agentengine.runtime.agents.Agent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;

public interface AgentFactory<C extends BaseAgentConfig, A extends Agent> {

    A build(C agentConfig);

    String type();
}
