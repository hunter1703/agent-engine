package com.agentengine.interfaces.rest.dto;

import com.agentengine.engine.beans.session.AgentSession;
import com.agui.core.event.BaseEvent;

import java.util.List;

public class AgentSessionDTO extends AgentSession {
  private List<BaseEvent> events;

  public AgentSessionDTO() {
  }

  public AgentSessionDTO(final AgentSession session, final List<BaseEvent> events) {
    super(session);
    this.events = events;
  }

  public List<BaseEvent> getEvents() {
    return events;
  }

  public void setEvents(final List<BaseEvent> events) {
    this.events = events;
  }
}
