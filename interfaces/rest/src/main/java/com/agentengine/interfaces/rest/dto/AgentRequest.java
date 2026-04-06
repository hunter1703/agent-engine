package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;
import java.util.Locale;

public class AgentRequest {

    @NotBlank(message = "agentId is required")
    private String agentId;

    private String sessionId;

    @NotBlank(message = "message is required")
    private String message;

    public AgentRequest() {}

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(final String agentId) {
        this.agentId = agentId;
    }

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
