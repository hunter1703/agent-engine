package com.agentengine.engine.agents;

import com.google.adk.runner.Runner;

public record AgentSessionRuntime(String sessionId, Runner runner) {}
