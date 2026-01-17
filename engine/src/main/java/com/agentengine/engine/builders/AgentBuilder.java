package com.agentengine.engine.builders;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.beans.config.AgentConfig;

public interface AgentBuilder {

  AgentEngine build(String agentName, AgentConfig agentConfig);

  String type();
}
