package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeKey = "type", seeAlso = {InMemorySessionServiceConfig.class, MongoSessionServiceConfig.class})
@BsonDiscriminator(key = "type")
public abstract class SessionServiceConfig implements Config {
  private String type;

  protected SessionServiceConfig(final SessionServiceType sessionServiceType) {
    this.type = sessionServiceType.name().toLowerCase();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum SessionServiceType {
    MEMORY,
    MONGODB
  }
}
