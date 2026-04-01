package com.agentengine.runtime.session.events;

public final class StartedFact extends SessionFact {
    private String message;

    public StartedFact() {}

    public StartedFact(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
