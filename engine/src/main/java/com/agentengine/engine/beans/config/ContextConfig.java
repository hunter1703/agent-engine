package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {LastNContextConfig.class, SummarizingContextConfig.class})
@BsonDiscriminator(key = "type")
public abstract class ContextConfig implements Config {
  private String type;

  protected ContextConfig(final ContextType contextType) {
    this.type = contextType.name().toLowerCase();
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  protected enum ContextType {
    SUMMARIZE, LAST_N
  }
}
