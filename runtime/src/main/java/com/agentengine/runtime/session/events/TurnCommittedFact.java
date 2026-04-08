package com.agentengine.runtime.session.events;

import com.google.adk.events.Event;
import java.util.List;

public final class TurnCommittedFact extends SessionFact {

    private List<Event> events;

    public TurnCommittedFact() {
        this(List.of());
    }

    public TurnCommittedFact(final List<Event> events) {
        setEvents(events);
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(final List<Event> events) {
        this.events = events == null ? List.of() : List.copyOf(events);
    }
}
