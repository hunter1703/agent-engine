package com.agentengine.api;

public class AgentEvent {
  private String event;
  private String sessionId;
  private Object payload;

  public AgentEvent() {}

  public AgentEvent(final String event, final String sessionId, final Object payload) {
    this.event = event;
    this.sessionId = sessionId;
    this.payload = payload;
  }

  public String getEvent() {
    return event;
  }

  public void setEvent(final String event) {
    this.event = event;
  }

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public Object getPayload() {
    return payload;
  }

  public void setPayload(final Object payload) {
    this.payload = payload;
  }
}
