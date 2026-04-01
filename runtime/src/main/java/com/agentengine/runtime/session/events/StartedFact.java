package com.agentengine.runtime.session.events;

import com.agentengine.util.common.beans.UniqueRecord;

public final class StartedFact extends SessionFact {
    private UniqueRecord<String> message;

    public StartedFact() {}

    public StartedFact(final UniqueRecord<String> message) {
        this.message = message;
    }

    public UniqueRecord<String> getMessage() {
        return message;
    }

    public void setMessage(final UniqueRecord<String> message) {
        this.message = message;
    }
}
