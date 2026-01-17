package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(typeName = "summarize")
public class SummarizingContextConfig extends ContextConfig {
  private Double triggerThreshold;

  private Double recencyThreshold;

  private String summarizerModel;

  public SummarizingContextConfig() {
    super(ContextType.SUMMARIZE);
  }

  public Double getTriggerThreshold() {
    return triggerThreshold;
  }

  public void setTriggerThreshold(final Double triggerThreshold) {
    this.triggerThreshold = triggerThreshold;
  }

  public Double getRecencyThreshold() {
    return recencyThreshold;
  }

  public void setRecencyThreshold(final Double recencyThreshold) {
    this.recencyThreshold = recencyThreshold;
  }

  public String getSummarizerModel() {
    return summarizerModel;
  }

  public void setSummarizerModel(final String summarizerModel) {
    this.summarizerModel = summarizerModel;
  }
}
