package com.agentengine.util.agents.agui;

/**
 * Custom AG-UI event emitted when a user responds to a confirmation request.
 *
 * <p>{@code confirmationId} matches the value from the preceding {@link
 * ConfirmationRequestedEvent}, allowing clients to correlate the two events.
 */
public final class ConfirmedEvent extends BaseCustomEvent {
    private String confirmationId;
    private boolean confirmed;
    private String answer;

    public ConfirmedEvent(final String confirmationId, final boolean confirmed, final String answer) {
        super("confirmed");
        this.confirmationId = confirmationId;
        this.confirmed = confirmed;
        this.answer = answer;
    }

    public ConfirmedEvent() {
        super("confirmed");
    }

    public String getConfirmationId() {
        return confirmationId;
    }

    public void setConfirmationId(final String confirmationId) {
        this.confirmationId = confirmationId;
    }

    public boolean isConfirmed() {
        return confirmed;
    }

    public void setConfirmed(final boolean confirmed) {
        this.confirmed = confirmed;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(final String answer) {
        this.answer = answer;
    }
}
