package com.agentengine.knowledge.api.beans;

import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.FileDetails;
import java.util.List;

public class Knowledge extends BaseEntity {
  public static final String FIELD_INDEXING_STATUS = "indexingStatus";
  public static final String FIELD_TOTAL_CHUNKS = "totalChunks";
  public static final String FIELD_INDEXED_AT = "indexedAt";
  public static final String FIELD_ERROR = "error";

  private String agentId;
  private List<String> grants;
  private FileDetails fileDetails;
  private String title;
  private String description;
  private KnowledgeSettings settings = new KnowledgeSettings();
  private String indexingStatus = IndexingStatus.PENDING.name();
  private int totalChunks;
  private long indexedAt;
  private String error;

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public List<String> getGrants() {
    return grants;
  }

  public void setGrants(final List<String> grants) {
    this.grants = grants;
  }

  public String getTitle() {
    return title;
  }

  public void setTitle(final String title) {
    this.title = title;
  }

  public String getDescription() {
    return description;
  }

  public KnowledgeSettings getSettings() {
    return settings;
  }

  public void setSettings(final KnowledgeSettings settings) {
    this.settings = settings;
  }

  public FileDetails getFileDetails() {
    return fileDetails;
  }

  public void setFileDetails(final FileDetails fileDetails) {
    this.fileDetails = fileDetails;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public String getIndexingStatus() {
    return indexingStatus;
  }

  public void setIndexingStatus(final String indexingStatus) {
    this.indexingStatus = indexingStatus == null ? IndexingStatus.PENDING.name() : indexingStatus;
  }

  public int getTotalChunks() {
    return totalChunks;
  }

  public void setTotalChunks(final int totalChunks) {
    this.totalChunks = totalChunks;
  }

  public long getIndexedAt() {
    return indexedAt;
  }

  public void setIndexedAt(final long indexedAt) {
    this.indexedAt = indexedAt;
  }

  public String getError() {
    return error;
  }

  public void setError(final String error) {
    this.error = error;
  }
}
