package com.agentengine.api;

public sealed interface AgentResponse permits InvokeResponse, PromptResponse {
  String sessionId();
}
