package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("summarize")
@BsonDiscriminator(value = "summarize")
public class SummarizeContextManagerConfig extends ContextManagerConfig {
  private int triggerTokens = 4096;
  private int recencyTokens = 1024;
  private String summaryModelId;

  public SummarizeContextManagerConfig() {
    super(ContextType.SUMMARIZE);
  }

  public int getTriggerTokens() {
    return triggerTokens;
  }

  public void setTriggerTokens(final int triggerTokens) {
    this.triggerTokens = triggerTokens;
  }

  public int getRecencyTokens() {
    return recencyTokens;
  }

  public void setRecencyTokens(final int recencyTokens) {
    this.recencyTokens = recencyTokens;
  }

  public String getSummaryModelId() {
    return summaryModelId;
  }

  public void setSummaryModelId(final String summaryModelId) {
    this.summaryModelId = summaryModelId;
  }
}
