package com.agentengine.engine.infra;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "default_model")
public class DefaultModelConfig extends InfraConfig {
  public static final String TYPE = "default_model";

  private String titleModelId;
  private String summaryModelId;
  private String evaluatorModelId;

  public DefaultModelConfig() {
    super(TYPE);
  }

  public String getTitleModelId() {
    return titleModelId;
  }

  public void setTitleModelId(final String titleModelId) {
    this.titleModelId = titleModelId;
  }

  public String getSummaryModelId() {
    return summaryModelId;
  }

  public void setSummaryModelId(final String summaryModelId) {
    this.summaryModelId = summaryModelId;
  }

  public String getEvaluatorModelId() {
    return evaluatorModelId;
  }

  public void setEvaluatorModelId(final String evaluatorModelId) {
    this.evaluatorModelId = evaluatorModelId;
  }
}
