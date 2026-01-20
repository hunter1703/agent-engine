package com.agentengine.interfaces.rest.dto;

import com.agentengine.interfaces.rest.dto.AgentResponse;

public record InvokeResponse(String sessionId, String finalAnswer, String thoughts) implements AgentResponse {
}
