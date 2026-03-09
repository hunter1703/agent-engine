package com.agentengine.engine.api.beans.config;

import com.fasterxml.jackson.annotation.JsonTypeName;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JsonTypeName("last_n")
@BsonDiscriminator(value = "last_n")
public class LastNContextStrategyConfig extends ContextStrategyConfig {
  private int keepLastTokens = 1024;

  public LastNContextStrategyConfig() {
    super(ContextStrategyType.LAST_N);
  }

  public int getKeepLastTokens() {
    return keepLastTokens;
  }

  public void setKeepLastTokens(final int keepLastTokens) {
    this.keepLastTokens = Math.max(1, keepLastTokens);
  }
}
