package com.agentengine.runtime.factories.agent;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.agentengine.engine.plugin.Agent;

public interface AgentFactory<C extends BaseAgentConfig, A extends Agent> {

  A build(C agentConfig);

  String type();
}
