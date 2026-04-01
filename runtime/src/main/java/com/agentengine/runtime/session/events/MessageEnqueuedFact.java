package com.agentengine.runtime.session.events;

import com.agentengine.util.common.beans.UniqueRecord;

public final class MessageEnqueuedFact extends SessionFact {

    private UniqueRecord<String> message;

    public MessageEnqueuedFact() {}

    public MessageEnqueuedFact(final UniqueRecord<String> message) {
        this.message = message;
    }

    public UniqueRecord<String> getMessage() {
        return message;
    }

    public void setMessage(final UniqueRecord<String> message) {
        this.message = message;
    }
}
