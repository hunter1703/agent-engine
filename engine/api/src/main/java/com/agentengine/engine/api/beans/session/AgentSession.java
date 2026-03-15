package com.agentengine.engine.api.beans.session;

import com.agentengine.util.common.Secure;
import com.agentengine.util.common.beans.NamedEntity;
import com.agui.core.event.BaseEvent;

import java.util.List;

public class AgentSession extends NamedEntity {
  public static final String DEFAULT_USER_ID = "default";
  public static final String FIELD_SESSION_INFO = "sessionInfo";
  public static final String FIELD_EVENTS = FIELD_SESSION_INFO + "." + "eventsJson";

  private String agentId;
  private SessionInfo sessionInfo;
  private List<BaseEvent> aguiEvents;

  public AgentSession() {
  }

  public AgentSession(final String id, final String agentId, final SessionInfo sessionInfo) {
    setId(id);
    setCreatedTime(System.currentTimeMillis());
    setUpdatedTime(System.currentTimeMillis());
    this.agentId = agentId;
    setName("Untitled Session");
    this.sessionInfo = sessionInfo;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) {
    this.agentId = agentId;
  }

  @Secure
  @Override
  public String getName() {
    return super.getName();
  }

  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  public void setSessionInfo(final SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }

  public List<BaseEvent> getAguiEvents() {
    return aguiEvents;
  }

  public void setAguiEvents(final List<BaseEvent> aguiEvents) {
    this.aguiEvents = aguiEvents;
  }
}
