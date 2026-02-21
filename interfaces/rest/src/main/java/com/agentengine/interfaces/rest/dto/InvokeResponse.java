package com.agentengine.interfaces.rest.dto;

public record InvokeResponse(String sessionId, String finalAnswer, String thoughts)
    implements AgentResponse {}
