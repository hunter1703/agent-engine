package com.agentengine.interfaces.rest.dto;

import com.agentengine.agent.api.model.UserMessage;
import jakarta.validation.Valid;

public class InvokeAgentRequest {

    private String sessionId;

    @Valid
    private UserMessage message;

    public InvokeAgentRequest() {}

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(final String sessionId) {
        this.sessionId = sessionId;
    }

    public UserMessage getMessage() {
        return message;
    }

    public void setMessage(final UserMessage message) {
        this.message = message;
    }
}
