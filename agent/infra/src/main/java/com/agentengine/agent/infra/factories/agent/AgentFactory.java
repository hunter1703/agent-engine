package com.agentengine.agent.infra.factories.agent;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.google.adk.agents.BaseAgent;

public interface AgentFactory<C extends BaseAgentConfig, A extends BaseAgent> {

  A build(C agentConfig);

  String type();
}
