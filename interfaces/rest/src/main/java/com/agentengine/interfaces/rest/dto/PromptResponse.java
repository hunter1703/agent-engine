package com.agentengine.interfaces.rest.dto;

import java.util.List;

public record PromptResponse(String sessionId, List<ContentDto> contents) implements AgentResponse {
}
