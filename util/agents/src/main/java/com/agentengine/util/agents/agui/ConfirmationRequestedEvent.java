package com.agentengine.util.agents.agui;

/**
 * Custom AG-UI event emitted when a session pauses to request human confirmation.
 *
 * <p>{@code confirmationId} is the ID the client must echo back via the confirm endpoint. It also
 * equals the {@code toolCallId} on the preceding {@code ToolCallStartEvent}, so clients can
 * correlate the two events without a separate field.
 */
public final class ConfirmationRequestedEvent extends BaseCustomEvent {
    private String confirmationId;

    public ConfirmationRequestedEvent(final String confirmationId) {
        super("confirmation_requested");
        this.confirmationId = confirmationId;
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(final String confirmationId) {
        this.confirmationId = confirmationId;
    }
}
