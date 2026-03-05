package com.agentengine.engine.api.beans.session;

import com.agentengine.engine.api.beans.BaseEntity;

public class SessionContextSummary extends BaseEntity {
  private String sessionId;
  private String agentId;
  private String summaryText;
  private int summarizedUntilContentCount;
  private int sourceTokens;
  private String modelId;

  public String getSessionId() {
    return sessionId;
  }

  public void setSessionId(final String sessionId) {
    this.sessionId = sessionId;
  }

  public String getAgentId() {
    return agentId;
  }

  public void setAgentId(final String agentId) {
    this.agentId = agentId;
  }

  public String getSummaryText() {
    return summaryText;
  }

  public void setSummaryText(final String summaryText) {
    this.summaryText = summaryText;
  }

  public int getSummarizedUntilContentCount() {
    return summarizedUntilContentCount;
  }

  public void setSummarizedUntilContentCount(final int summarizedUntilContentCount) {
    this.summarizedUntilContentCount = summarizedUntilContentCount;
  }

  public int getSourceTokens() {
    return sourceTokens;
  }

  public void setSourceTokens(final int sourceTokens) {
    this.sourceTokens = sourceTokens;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(final String modelId) {
    this.modelId = modelId;
  }
}
