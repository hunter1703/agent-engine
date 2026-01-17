package com.agentengine.api;

public record InvokeResponse(String sessionId, String finalAnswer, String thoughts)
    implements AgentResponse {}
