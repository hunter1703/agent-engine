package com.agentengine.runtime.session.events;

import com.google.adk.events.Event;

import java.util.List;

public final class TurnCommittedFact extends SessionFact {

  private List<Event> events;
  private long startSequence;
  private String failure;
  private String finalAnswer;

  public TurnCommittedFact() {
    this(List.of(), 0L, null, null);
  }

  public TurnCommittedFact(final List<Event> events, final long startSequence, final String failure, final String finalAnswer) {
    setEvents(events);
    this.startSequence = startSequence;
    this.failure = failure;
    this.finalAnswer = finalAnswer;
  }

  public List<Event> getEvents() {
    return events;
  }

  public void setEvents(final List<Event> events) {
    this.events = events == null ? List.of() : List.copyOf(events);
  }

  public long getStartSequence() {
    return startSequence;
  }

  public void setStartSequence(final long startSequence) {
    this.startSequence = startSequence;
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
