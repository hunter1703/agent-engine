package com.agentengine.agent.infra.factories.agent;

import com.agentengine.agent.infra.agents.Agent;
import com.agentengine.util.agents.beans.config.BaseAgentConfig;

public interface AgentFactory<C extends BaseAgentConfig, A extends Agent> {

  A build(C agentConfig);

  String type();
}
