package com.agentengine.engine.builders;

import com.agentengine.engine.AgentEngine;
import com.agentengine.engine.beans.config.AgentConfig;
import java.util.List;

public interface AgentBuilder {

  AgentEngine build(String agentName, AgentConfig agentConfig);

  List<String> agentNames();
}
