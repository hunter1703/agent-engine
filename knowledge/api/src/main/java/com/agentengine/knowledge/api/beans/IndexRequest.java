package com.agentengine.knowledge.api.beans;

import com.agentengine.util.agents.beans.config.KnowledgeSettings;
import com.agentengine.util.common.beans.FileDetails;
import java.util.ArrayList;
import java.util.List;

public class IndexRequest {

  /** The id the knowledge is created with. Knowledge that has this id is replaced and indexed again. */
  private String knowledgeId;

  private FileDetails fileDetails;
  private String title;
  private String description;
  private List<String> tags = new ArrayList<>();
  private KnowledgeSettings settings;
  private boolean waitForCompletion;
  private boolean skipIndexing;

  public String getKnowledgeId() {
    return knowledgeId;
  }

  public void setKnowledgeId(final String knowledgeId) {
    this.knowledgeId = knowledgeId;
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

  public List<String> getTags() {
    return tags;
  }

  public void setTags(final List<String> tags) {
    this.tags = tags == null ? new ArrayList<>() : tags;
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
