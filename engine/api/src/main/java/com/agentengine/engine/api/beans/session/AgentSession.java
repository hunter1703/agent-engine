package com.agentengine.engine.api.beans.session;

import com.agentengine.engine.api.beans.BaseEntity;
import com.google.adk.sessions.Session;

public class AgentSession extends BaseEntity {
  public static final String DEFAULT_USER_ID = "default";
  public static final String DEFAULT_APP = "default";
  public static final String FIELD_TITLE = "title";
  public static final String FIELD_SESSION_INFO = "sessionInfo";

  private String agentId;
  private String userId;
  private String title;
  private SessionInfo sessionInfo;

  public AgentSession() {}

  public AgentSession(final String id, final String agentId, final Session session) {
    setId(id);
    setCreatedTime(System.currentTimeMillis());
    setUpdatedTime(System.currentTimeMillis());
    this.agentId = agentId;
    this.userId = DEFAULT_USER_ID;
    this.title = "Untitled Session";
    this.sessionInfo = SessionInfo.fromSession(session);
  }

  public AgentSession(final AgentSession other) {
    setId(other.getId());
    setCreatedTime(other.getCreatedTime());
    setUpdatedTime(other.getUpdatedTime());
    this.agentId = other.agentId;
    this.userId = other.userId;
    this.title = other.title;
    this.sessionInfo = other.sessionInfo;
  }

  public AgentSession(final String id, final String agentId, final String title) {
    setId(id);
    setCreatedTime(System.currentTimeMillis());
    setUpdatedTime(System.currentTimeMillis());
    this.agentId = agentId;
    this.userId = DEFAULT_USER_ID;
    this.title = title;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(String agentId) {
    this.agentId = agentId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public SessionInfo getSessionInfo() {
    return sessionInfo;
  }

  public void setSessionInfo(final SessionInfo sessionInfo) {
    this.sessionInfo = sessionInfo;
  }
}
