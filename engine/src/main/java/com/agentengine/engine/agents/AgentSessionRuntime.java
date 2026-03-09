package com.agentengine.engine.agents;

import com.google.adk.runner.Runner;
import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import java.util.Map;

public record AgentSessionRuntime(String sessionId, Runner runner, BaseAgentConfig agentConfig) {}
