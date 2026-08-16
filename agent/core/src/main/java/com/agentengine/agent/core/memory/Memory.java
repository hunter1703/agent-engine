package com.agentengine.agent.core.memory;

import com.agentengine.util.vectordb.VectorEntity;

/**
 * A single persistent memory entry stored in the vector store.
 *
 * <p>Scoped to a user within an agen). The {@code text} field is embedded as the searchable vector,
 * allowing semantic retrieval across sessions.
 */
public class Memory extends VectorEntity {

  public static final String FIELD_AGENT_ID = "agentId";
  public static final String FIELD_USER_ID = "userId";
  public static final String FIELD_TEXT = "text";

  private String agentId;
  private String userId;
  private String text;

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(final String userId) {
    this.userId = userId;
  }

  public String getText() {
    return text;
  }

  public void setText(final String text) {
    this.text = text;
  }
}
