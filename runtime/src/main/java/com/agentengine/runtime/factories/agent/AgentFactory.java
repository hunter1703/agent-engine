package com.agentengine.runtime.factories.agent;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.agentengine.runtime.agents.Agent;

public interface AgentFactory<C extends BaseAgentConfig, A extends Agent> {

  A build(C agentConfig);

  String type();
}
