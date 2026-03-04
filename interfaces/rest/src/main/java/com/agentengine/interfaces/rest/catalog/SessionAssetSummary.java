package com.agentengine.interfaces.rest.catalog;

import com.agentengine.interfaces.rest.dto.AgentSessionDTO;

public class SessionAssetSummary extends NameIdEntity {
  private final String agentId;
  private final Long createdTime;
  private final Long updatedTime;

  public SessionAssetSummary(
      final String id,
      final String name,
      final String agentId,
      final Long createdTime,
      final Long updatedTime) {
    super(id, name);
    this.agentId = agentId;
    this.createdTime = createdTime;
    this.updatedTime = updatedTime;
  }

  public static SessionAssetSummary fromSession(final AgentSessionDTO session, final String name) {
    if (session == null) {
      return new SessionAssetSummary(null, null, null, null, null);
    }
    return new SessionAssetSummary(
        session.getId(),
        name,
        session.getAgentId(),
        session.getCreatedTime(),
        session.getUpdatedTime());
  }

  public String getAgentId() {
    return agentId;
  }

  public Long getCreatedTime() {
    return createdTime;
  }

  public Long getUpdatedTime() {
    return updatedTime;
  }
}
