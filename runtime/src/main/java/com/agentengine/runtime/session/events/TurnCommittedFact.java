package com.agentengine.runtime.session.events;

import com.google.adk.events.Event;
import java.util.List;

public final class TurnCommittedFact extends SessionFact {

    private List<Event> events;
    private String failure;
    private String finalAnswer;

    public TurnCommittedFact() {
        this(List.of(), null, null);
    }

    public TurnCommittedFact(final List<Event> events, final String failure, final String finalAnswer) {
        setEvents(events);
        this.failure = failure;
        this.finalAnswer = finalAnswer;
    }

    public List<Event> getEvents() {
        return events;
    }

    public void setEvents(final List<Event> events) {
        this.events = events == null ? List.of() : List.copyOf(events);
    }

    public String getFailure() {
        return failure;
    }

    public void setFailure(final String failure) {
        this.failure = failure;
    }

    public String getFinalAnswer() {
        return finalAnswer;
    }

    public void setFinalAnswer(final String finalAnswer) {
        this.finalAnswer = finalAnswer;
    }
}
