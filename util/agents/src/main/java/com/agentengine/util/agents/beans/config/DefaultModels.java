package com.agentengine.util.agents.beans.config;

import com.agentengine.util.common.beans.BaseEntity;

public class DefaultModels extends BaseEntity {

  public static final String ID = "default";
  public static final String CHAT_ID = "default-chat";
  public static final String VISION_ID = "default-vision";

  private String fastModelId;
  private String compactionModelId;
  private String evaluatorModelId;
  private String embeddingModelId;
  private String chatModelId;
  private String visionModelId;

  public DefaultModels() {
    super(ID);
  }

  public String getFastModelId() {
    return fastModelId;
  }

  public void setFastModelId(final String fastModelId) {
    this.fastModelId = fastModelId;
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

  public String getVisionModelId() {
    return visionModelId;
  }

  public void setVisionModelId(final String visionModelId) {
    this.visionModelId = visionModelId;
  }
}
