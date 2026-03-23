package com.agentengine.runtime.agents;

import com.agentengine.runtime.api.beans.config.BaseAgentConfig;
import com.google.adk.runner.Runner;

public record AgentSessionRuntime(String sessionId, Runner runner, BaseAgentConfig agentConfig) {
}
