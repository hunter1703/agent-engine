package com.agentengine.knowledge.api.beans;

import com.agentengine.util.common.annotations.Indexed;
import com.agentengine.util.common.beans.FileDetails;
import com.agentengine.util.common.events.Copyable;
import com.agentengine.util.vectordb.VectorEntity;

/**
 * A piece of a knowledge item stored in the vector store, with the vectors of one embedding model
 * ({@link #embedder}) for its {@link #text}, {@link #description} and {@link #media}.
 *
 * <p>The chunks made from one piece of the item, by different models or, for an image, by
 * different vectors found in it, share its {@link #chunkIndex}, {@link #chunkStart} and {@link
 * #chunkEnd}. Their ids are their own.
 *
 * <p>The item's source and source type are on the parent {@link Knowledge}, reached through {@link
 * #knowledgeId}.
 */
public class KnowledgeChunk extends VectorEntity implements Copyable<KnowledgeChunk> {

  public static final String FIELD_KNOWLEDGE_ID = "knowledgeId";
  public static final String FIELD_CHUNK_INDEX = "chunkIndex";
  public static final String FIELD_CHUNK_START = "chunkStart";
  public static final String FIELD_CHUNK_END = "chunkEnd";
  public static final String FIELD_KNOWLEDGE_VERSION = "knowledgeVersion";
  public static final String FIELD_STATUS = "status";

  public static final String FIELD_TEXT = "text";
  public static final String FIELD_MEDIA = "media";
  public static final String FIELD_DESCRIPTION = "description";
  public static final String FIELD_EMBEDDER = "embedder";

  @Indexed private String knowledgeId;
  @Indexed private Long knowledgeVersion;
  @Indexed private String status;
  private int chunkIndex;

  @Indexed(vector = true)
  private String text;

  @Indexed(vector = true)
  private String description;

  @Indexed(vector = true)
  private FileDetails media;

  /** The id of the embedding model that produced the chunk's vectors. */
  @Indexed private String embedder;

  private int chunkStart;
  private int chunkEnd;

  public String getKnowledgeId() {
    return knowledgeId;
  }

  public void setKnowledgeId(final String knowledgeId) {
    this.knowledgeId = knowledgeId;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  public Long getKnowledgeVersion() {
    return knowledgeVersion;
  }

  public void setKnowledgeVersion(final Long knowledgeVersion) {
    this.knowledgeVersion = knowledgeVersion;
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

  public FileDetails getMedia() {
    return media;
  }

  public void setMedia(FileDetails media) {
    this.media = media;
  }

  public String getEmbedder() {
    return embedder;
  }

  public void setEmbedder(final String embedder) {
    this.embedder = embedder;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public KnowledgeChunk withEmbedder(final String embedder) {
    setEmbedder(embedder);
    return this;
  }

  @Override
  public KnowledgeChunk copy() {
    final KnowledgeChunk copy = new KnowledgeChunk();
    copy.knowledgeId = this.knowledgeId;
    copy.knowledgeVersion = this.knowledgeVersion;
    copy.status = this.status;
    copy.chunkIndex = this.chunkIndex;
    copy.text = this.text;
    copy.description = this.description;
    copy.media = this.media;
    copy.embedder = this.embedder;
    copy.chunkStart = this.chunkStart;
    copy.chunkEnd = this.chunkEnd;
    return copy;
  }
}
