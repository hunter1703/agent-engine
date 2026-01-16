package com.localagent.engine.builders;

import com.localagent.engine.AgentEngine;
import com.localagent.engine.beans.config.AgentConfig;
import java.util.List;

public interface AgentBuilder {

  AgentEngine build(String agentName, AgentConfig agentConfig);

  List<String> agentNames();
}
