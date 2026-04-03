package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmSessionRequest extends AgentRequest {
    private Boolean confirmed;

    @NotBlank(message = "Confirmation Id is required")
    private String confirmationId;

    public ConfirmSessionRequest() {}

    public ConfirmSessionRequest(final String answer) {
        this(answer, null, null);
    }

    public ConfirmSessionRequest(final String answer, final Boolean confirmed, final String confirmationId) {
        setMessage(answer);
        this.confirmed = confirmed;
        this.confirmationId = confirmationId;
    }

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
}
