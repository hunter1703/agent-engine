package com.agentengine.runtime.session.events;

public final class MessageEnqueuedFact extends SessionFact {

    private String message;

    public MessageEnqueuedFact() {}

    public MessageEnqueuedFact(final String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(final String message) {
        this.message = message;
    }
}
