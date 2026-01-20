package com.agentengine.interfaces.rest.dto;

import java.util.List;

public record PromptResponse(String sessionId, List<MessageDto> messages) implements AgentResponse {
}
