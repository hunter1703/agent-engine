package com.agentengine.util.agents.agui;

import java.util.List;

/**
 * Custom AG-UI event emitted when a session pauses to request human confirmation.
 *
 * <p>{@code confirmationId} is the ID the client must echo back via the confirm endpoint. It
 * matches the ID of the underlying {@code adk_request_confirmation} function call, allowing the
 * server to route the response back to the correct tool invocation.
 */
public final class ConfirmationRequestedEvent extends BaseCustomEvent {
    private String confirmationId;
    private String prompt;
    private String originalToolCallId;
    private List<String> options;

    public ConfirmationRequestedEvent(
            final String confirmationId,
            final String prompt,
            final String originalToolCallId,
            final List<String> options) {
        super("confirmation_requested");
        this.confirmationId = confirmationId;
        this.prompt = prompt;
        this.originalToolCallId = originalToolCallId;
        this.options = options;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(final String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public String getPrompt() {
        return prompt;
    }

    public void setPrompt(final String prompt) {
        this.prompt = prompt;
    }

    public String getOriginalToolCallId() {
        return originalToolCallId;
    }

    public void setOriginalToolCallId(final String originalToolCallId) {
        this.originalToolCallId = originalToolCallId;
    }

    public List<String> getOptions() {
        return options;
    }

    public void setOptions(final List<String> options) {
        this.options = options;
    }
}
