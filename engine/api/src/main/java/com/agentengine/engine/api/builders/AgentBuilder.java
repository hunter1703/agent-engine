package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;

public interface AgentBuilder<C extends BaseAgentConfig, A extends Agent> {

  A build(C agentConfig);

  String type();
}
