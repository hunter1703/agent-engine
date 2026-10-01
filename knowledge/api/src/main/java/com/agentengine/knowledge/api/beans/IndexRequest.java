package com.agentengine.knowledge.api.beans;

import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.common.beans.FileDetails;

public class IndexRequest {

  private String agentId;
  private FileDetails fileDetails;
  private String title;
  private String description;
  private KnowledgeSettings settings;
  private boolean waitForCompletion;
  private boolean skipIndexing;

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public FileDetails getFileDetails() {
    return fileDetails;
  }

  public void setFileDetails(final FileDetails fileDetails) {
    this.fileDetails = fileDetails;
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

  public void setDescription(final String description) {
    this.description = description;
  }

  public KnowledgeSettings getSettings() {
    return settings;
  }

  public void setSettings(final KnowledgeSettings settings) {
    this.settings = settings;
  }

  public boolean isWaitForCompletion() {
    return waitForCompletion;
  }

  public void setWaitForCompletion(final boolean waitForCompletion) {
    this.waitForCompletion = waitForCompletion;
  }

  public boolean isSkipIndexing() {
    return skipIndexing;
  }

  public void setSkipIndexing(final boolean skipIndexing) {
    this.skipIndexing = skipIndexing;
  }
}
