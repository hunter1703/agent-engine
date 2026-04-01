package com.agentengine.runtime.session.events;

import com.agentengine.util.pekko.PekkoSerializable;
import java.time.Instant;

public abstract class SessionFact implements PekkoSerializable {

    private Instant timestamp;

    protected SessionFact() {
        this.timestamp = Instant.now();
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(final Instant timestamp) {
        this.timestamp = timestamp;
    }
}
