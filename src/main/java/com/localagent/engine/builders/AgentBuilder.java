package com.localagent.engine.builders;

import com.localagent.engine.AgentEngine;
import com.localagent.engine.DefaultAgentEngine;
import com.localagent.engine.config.AgentConfig;

import java.nio.file.Path;
import java.util.List;

public interface AgentBuilder {

    AgentEngine build(String agentName, AgentConfig agentConfig);

    List<String> agentNames();
}
