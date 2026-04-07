package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class InvokeAgentRequest {

    private String sessionId;

    @NotBlank(message = "message is required")
    private String message;

    public InvokeAgentRequest() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
