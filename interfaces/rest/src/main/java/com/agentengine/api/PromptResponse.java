package com.agentengine.api;

import java.util.List;

public record PromptResponse(String sessionId, List<MessageDto> messages) {}
