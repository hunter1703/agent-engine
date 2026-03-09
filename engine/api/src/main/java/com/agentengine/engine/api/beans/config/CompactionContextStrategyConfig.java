package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("compaction")
@BsonDiscriminator(value = "compaction")
public class CompactionContextStrategyConfig extends ContextStrategyConfig {
  private boolean enabled = true;
  private int tokenThreshold = 4096;
  private int recencyThreshold = 1024;
  private String modelId;
  private String promptTemplate;

  public CompactionContextStrategyConfig() {
    super(ContextStrategyType.COMPACTION);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public void setEnabled(final boolean enabled) {
    this.enabled = enabled;
  }

  public int getTokenThreshold() {
    return tokenThreshold;
  }

  public void setTokenThreshold(final int tokenThreshold) {
    this.tokenThreshold = tokenThreshold;
  }

  public int getRecencyThreshold() {
    return recencyThreshold;
  }

  public void setRecencyThreshold(final int recencyThreshold) {
    this.recencyThreshold = recencyThreshold;
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(final String modelId) {
    this.modelId = modelId;
  }

  public String getPromptTemplate() {
    return promptTemplate;
  }

  public void setPromptTemplate(final String promptTemplate) {
    this.promptTemplate = promptTemplate;
  }
}
