package com.agentengine.engine.api.builders;

import com.agentengine.engine.api.Agent;
import com.agentengine.engine.api.beans.config.AgentConfig;

public interface AgentBuilder {

  Agent build(String agentName, AgentConfig agentConfig);

  String type();
}
