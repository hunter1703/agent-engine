package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.beans.BaseEntity;

public class DefaultModels extends BaseEntity {

  public static final String ID = "default";

  private String titleModelId;
  private String compactionModelId;
  private String evaluatorModelId;
  private String embeddingModelId;
  private String chatModelId;

  public DefaultModels() {
    super(ID);
  }

  public String getTitleModelId() {
    return titleModelId;
  }

  public void setTitleModelId(final String titleModelId) {
    this.titleModelId = titleModelId;
  }

  public String getCompactionModelId() {
    return compactionModelId;
  }

  public void setCompactionModelId(final String compactionModelId) {
    this.compactionModelId = compactionModelId;
  }

  public String getEvaluatorModelId() {
    return evaluatorModelId;
  }

  public void setEvaluatorModelId(final String evaluatorModelId) {
    this.evaluatorModelId = evaluatorModelId;
  }

  public String getEmbeddingModelId() {
    return embeddingModelId;
  }

  public void setEmbeddingModelId(final String embeddingModelId) {
    this.embeddingModelId = embeddingModelId;
  }

  public String getChatModelId() {
    return chatModelId;
  }

  public void setChatModelId(final String chatModelId) {
    this.chatModelId = chatModelId;
  }
}
