package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {InMemorySessionStoreConfig.class})
@BsonDiscriminator(key = "type")
public abstract class SessionStoreConfig implements Config {

  private String type;

  protected SessionStoreConfig(final SessionStoreType sessionStoreType) {
    this.type = sessionStoreType.name().toLowerCase();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum SessionStoreType {
    MEMORY
  }
}