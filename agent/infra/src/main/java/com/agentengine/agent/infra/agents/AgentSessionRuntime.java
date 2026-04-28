package com.agentengine.agent.infra.agents;

import com.agentengine.util.agents.beans.config.BaseAgentConfig;
import com.google.adk.runner.Runner;

public record AgentSessionRuntime(String sessionId, Runner runner, BaseAgentConfig agentConfig) {}
