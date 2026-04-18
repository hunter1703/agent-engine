package com.agentengine.runtime.session.events;

import com.agentengine.runtime.api.model.UserMessage;
import com.agentengine.util.common.beans.UniqueRecord;

public final class StartedFact extends SessionFact {
    private UniqueRecord<UserMessage> message;

    public StartedFact() {}

    public StartedFact(final UniqueRecord<UserMessage> message) {
        this.message = message;
    }

    public UniqueRecord<UserMessage> getMessage() {
        return message;
    }

    public void setMessage(final UniqueRecord<UserMessage> message) {
        this.message = message;
    }
}
