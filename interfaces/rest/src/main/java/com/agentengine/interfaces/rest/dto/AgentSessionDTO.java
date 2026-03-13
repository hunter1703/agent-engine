package com.agentengine.interfaces.rest.dto;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agui.core.event.BaseEvent;
import java.util.List;

public class AgentSessionDTO extends AgentSession {
  private List<BaseEvent> events;

  public AgentSessionDTO() {
  }

  public AgentSessionDTO(final AgentSession session, final List<BaseEvent> events) {
    super(session);
    setSessionInfo(null);
    this.events = events;
  }

  public static AgentSessionDTO summary(final AgentSession session) {
    final AgentSessionDTO dto = new AgentSessionDTO();
    dto.setId(session.getId());
    dto.setAgentId(session.getAgentId());
    dto.setName(session.getName());
    dto.setCreatedTime(session.getCreatedTime());
    dto.setUpdatedTime(session.getUpdatedTime());
    return dto;
  }

  public List<BaseEvent> getEvents() {
    return events;
  }

  public void setEvents(final List<BaseEvent> events) {
    this.events = events;
  }
}
