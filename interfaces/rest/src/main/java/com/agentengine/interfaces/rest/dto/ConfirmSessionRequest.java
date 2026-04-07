package com.agentengine.interfaces.rest.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmSessionRequest {
    private Boolean confirmed;
    private String message;

    public ConfirmSessionRequest() {}

    public Boolean getConfirmed() {
        return confirmed;
    }

    public void setConfirmed(final Boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
