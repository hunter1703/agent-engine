package com.agentengine.runtime.session.events;

import com.agentengine.util.agents.beans.Confirmation;

public final class ConfirmedFact extends SessionFact {

    private Confirmation confirmation;

    public ConfirmedFact() {}

    public ConfirmedFact(final Confirmation confirmation) {
        this.confirmation = confirmation;
    }

    public Confirmation getConfirmation() {
        return confirmation;
    }

    public void setConfirmation(final Confirmation confirmation) {
        this.confirmation = confirmation;
    }
}
