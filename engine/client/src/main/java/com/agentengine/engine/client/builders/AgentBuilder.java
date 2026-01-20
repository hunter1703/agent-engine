package com.agentengine.engine.client.builders;

import com.agentengine.engine.client.AgentEngine;
import com.agentengine.engine.client.beans.config.AgentConfig;

public interface AgentBuilder {

  AgentEngine build(String agentName, AgentConfig agentConfig);

  String type();
}
