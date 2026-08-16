package com.agentengine.knowledge.api.beans;

import com.agentengine.util.vectordb.VectorEntity;

/**
 * A single indexed chunk stored in the vector store. The {@code id} is the point ID ({@code
 * knowledgeId + "-" + chunkIndex}).
 *
 * <p>Source and sourceType are intentionally omitted — they are available on the parent {@link
 * Knowledge} entity and can be fetched via {@code knowledgeId} when needed.
 *
 * <p>Currently a single vector field is used ({@link #FIELD_TEXT_VECTOR}), populated by embedding
 * the chunk's text. Additional fields (e.g. title, summary) can be added later by calling {@link
 * #setVector(String, float[])} with the appropriate physical field name.
 */
public class KnowledgeChunk extends VectorEntity {

  public static final String FIELD_KNOWLEDGE_ID = "knowledgeId";
  public static final String FIELD_AGENT_ID = "agentId";

  /** Physical vector field name for the primary text embedding in the vector store. */
  public static final String FIELD_TEXT_VECTOR = "textVector";

  private String knowledgeId;
  private String agentId;
  private int chunkIndex;
  private String text;
  private int chunkStart;
  private int chunkEnd;

  public String getKnowledgeId() {
    return knowledgeId;
  }

  public void setKnowledgeId(final String knowledgeId) {
    this.knowledgeId = knowledgeId;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public int getChunkIndex() {
    return chunkIndex;
  }

  public void setChunkIndex(final int chunkIndex) {
    this.chunkIndex = chunkIndex;
  }

  public String getText() {
    return text;
  }

  public void setText(final String text) {
    this.text = text;
  }

  public int getChunkStart() {
    return chunkStart;
  }

  public void setChunkStart(final int chunkStart) {
    this.chunkStart = chunkStart;
  }

  public int getChunkEnd() {
    return chunkEnd;
  }

  public void setChunkEnd(final int chunkEnd) {
    this.chunkEnd = chunkEnd;
  }
}
