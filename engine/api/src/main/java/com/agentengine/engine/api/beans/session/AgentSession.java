package com.agentengine.engine.api.beans.session;

import com.agentengine.util.beans.NamedEntity;
import com.agentengine.util.Secure;
import com.google.adk.sessions.Session;

public class AgentSession extends NamedEntity {
  public static final String DEFAULT_USER_ID = "default";
  public static final String FIELD_SESSION_INFO = "sessionInfo";

  private String agentId;
  private SessionInfo sessionInfo;

  public AgentSession() {}

  public AgentSession(final String id, final String agentId, final Session session) {
    setId(id);
    setCreatedTime(System.currentTimeMillis());
    setUpdatedTime(System.currentTimeMillis());
    this.agentId = agentId;
    setName("Untitled Session");
    this.sessionInfo = SessionInfo.fromSession(session);
  }

  public AgentSession(final AgentSession other) {
    setId(other.getId());
    setCreatedTime(other.getCreatedTime());
    setUpdatedTime(other.getUpdatedTime());
    this.agentId = other.agentId;
    setName(other.getName());
    this.sessionInfo = other.sessionInfo;
  }

  public AgentSession(final String id, final String agentId, final String title) {
    setId(id);
    setCreatedTime(System.currentTimeMillis());
    setUpdatedTime(System.currentTimeMillis());
    this.agentId = agentId;
    setName(title);
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
}
