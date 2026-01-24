package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;

public interface AgentBuilder<C extends AgentConfig, A extends Agent> {

  A build(C agentConfig);

  String type();
}
