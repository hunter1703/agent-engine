package com.agentengine.interfaces.rest.dto;

public sealed interface AgentResponse permits InvokeResponse, PromptResponse {
  String sessionId();
}
