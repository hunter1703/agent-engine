package com.agentengine.interfaces.rest.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(oneOf = {InvokeResponse.class, PromptResponse.class})
public sealed interface AgentResponse permits InvokeResponse, PromptResponse {
  String sessionId();
}
