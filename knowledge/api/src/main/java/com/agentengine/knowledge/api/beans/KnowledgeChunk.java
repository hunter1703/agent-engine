package com.agentengine.knowledge.api.beans;

import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.vectordb.VectorEntity;

/**
 * A single indexed chunk stored in the vector store. The {@code id} is the point ID ({@code
 * knowledgeId + "-" + chunkIndex}).
 *
 * <p>Source and sourceType are intentionally omitted — they are available on the parent {@link
 * Knowledge} entity and can be fetched via {@code knowledgeId} when needed.
 *
 * <p>Currently a single vector field is used ({@link #FIELD_TEXT}), populated by embedding the
 * chunk's text. Additional fields (e.g. title, summary) can be added later by calling {@link
 * #setVector(String, float[])} with the appropriate physical field name.
 */
@Permissioned(assetClass = AssetClass.KNOWLEDGE_CHUNK)
public class KnowledgeChunk extends VectorEntity {

  public static final String FIELD_KNOWLEDGE_ID = "knowledgeId";
  public static final String FIELD_AGENT_ID = "agentId";

  public static final String FIELD_TEXT = "text";

  public static final String ADDITIONAL_AGENT_ID = "agentId";

  public static final String ADDITIONAL_SESSION_ID = "sessionId";

  /** Default {@link #mimeType} for a chunk whose content is text, as almost every chunk's is. */
  public static final String MIME_TYPE_TEXT = "text/plain";

  @Indexed private String knowledgeId;
  @Indexed private String agentId;
  private int chunkIndex;

  @Indexed(vector = true)
  private String text;

  /**
   * The kind of content this chunk carries — {@link #MIME_TYPE_TEXT} unless it's still an
   * undescribed media chunk (see {@link #bytes}), in which case it's that media's own mime type
   * (e.g. {@code image/png}). A stage whose logic only makes sense for text (splitting by sentence,
   * merging by embedding similarity) checks this rather than assuming every chunk it sees is text.
   */
  private String mimeType = MIME_TYPE_TEXT;

  /**
   * Raw bytes of the media (e.g. an image) this chunk represents, set only while it's still
   * awaiting description by a media-description stage — {@code null} once description has replaced
   * it with {@link #text} (and {@link #mimeType} reverts to {@link #MIME_TYPE_TEXT}), and for every
   * chunk that was never media. Pipeline-internal state, never persisted.
   */
  private transient byte[] bytes;

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

  public String getMimeType() {
    return mimeType;
  }

  public void setMimeType(final String mimeType) {
    this.mimeType = mimeType;
  }

  public byte[] getBytes() {
    return bytes;
  }

  public void setBytes(final byte[] bytes) {
    this.bytes = bytes;
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
