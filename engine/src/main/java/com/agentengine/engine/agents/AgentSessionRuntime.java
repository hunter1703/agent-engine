package com.agentengine.engine.agents;

import com.agentengine.engine.api.beans.config.BaseAgentConfig;
import com.google.adk.runner.Runner;

public record AgentSessionRuntime(String sessionId, Runner runner, BaseAgentConfig agentConfig) {
}
