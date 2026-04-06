package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmSessionRequest {
    private Boolean confirmed;

    @NotBlank(message = "Confirmation Id is required")
    private String confirmationId;
    @NotBlank(message = "Session Id is required")
    private String sessionId;
    private String message;

    public ConfirmSessionRequest() {}

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(final Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(final String confirmationId) {
        this.confirmationId = confirmationId;
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
