package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {LastNContextManagerConfig.class})
@BsonDiscriminator(key = "type")
public abstract class ContextManagerConfig implements Config {
  private String type;

  protected ContextManagerConfig(final ContextType contextType) {
    this.type = contextType.name().toLowerCase();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum ContextType {
    SUMMARIZE, LAST_N
  }
}
