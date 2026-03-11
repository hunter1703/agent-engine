package com.agentengine.engine.api.factories;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;

public interface AgentFactory<C extends BaseAgentConfig, A extends Agent> {

  A build(C agentConfig);

  String type();
}
