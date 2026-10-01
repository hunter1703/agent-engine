package com.agentengine.agent.core.memory;

import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.vectordb.VectorEntity;

/**
 * A single persistent memory entry stored in the vector store.
 *
 * <p>Belongs to an agent, for the user it was made for: it is recalled by that agent acting for
 * that user, in any of their sessions. The {@code text} field is embedded as the searchable vector,
 * allowing semantic retrieval across sessions.
 */
@Permissioned(assetClass = AssetClass.MEMORY)
public class Memory extends VectorEntity {

  public static final String FIELD_AGENT_ID = "agentId";
  public static final String FIELD_TEXT = "text";

  @Indexed private String agentId;

  @Indexed(vector = true)
  private String text;

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getText() {
    return text;
  }

  public void setText(final String text) {
    this.text = text;
  }
}
