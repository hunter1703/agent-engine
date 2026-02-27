package com.agentengine.engine.infra;

import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator(value = "title_model")
public class TitleConfig extends InfraConfig {
  public static final String TYPE = "title_model";
  private String modelId;

  public TitleConfig() {
    super(TYPE);
  }

  public String getModelId() {
    return modelId;
  }

  public void setModelId(final String modelId) {
    this.modelId = modelId;
  }
}
