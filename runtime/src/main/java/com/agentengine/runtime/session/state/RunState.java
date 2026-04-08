package com.agentengine.runtime.session.state;

import com.agentengine.util.common.beans.UniqueRecord;
import com.google.adk.events.Event;
import java.util.List;

public record RunState(List<Event> lastCommittedTurn, UniqueRecord<String> message) {

    public RunState withEvents(final List<Event> events) {
        return new RunState(events, message);
    }

    public RunState withMessage(final UniqueRecord<String> message) {
        return new RunState(lastCommittedTurn, message);
    }
}
