package com.agentengine.engine.api.beans.session;

public class AgentSession {
  public static final String DEFAULT_USER_ID = "default";
  public static final String FIELD_TITLE = "title";

  private String id;
  private String agentId;
  private String userId;
  private long createdAt;
  private long updatedAt;
  private String title;

  public AgentSession() {
  }

  public AgentSession(String id, String agentId, String title) {
    this.id = id;
    this.agentId = agentId;
    this.userId = DEFAULT_USER_ID;
    this.createdAt = System.currentTimeMillis();
    this.updatedAt = System.currentTimeMillis();
    this.title = title;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
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

  public long getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(long createdAt) {
    this.createdAt = createdAt;
  }

  public long getUpdatedAt() {
    return updatedAt;
  }

  public void setUpdatedAt(long updatedAt) {
    this.updatedAt = updatedAt;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(String title) {
    this.title = title;
  }
}