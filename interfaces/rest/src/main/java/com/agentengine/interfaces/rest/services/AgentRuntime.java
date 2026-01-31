package com.agentengine.interfaces.rest.services;

import com.google.adk.agents.LlmAgent;
import com.google.adk.runner.Runner;
import com.google.adk.sessions.BaseSessionService;

public record AgentRuntime(LlmAgent agent, Runner runner, BaseSessionService sessionService, String appName) {
}
