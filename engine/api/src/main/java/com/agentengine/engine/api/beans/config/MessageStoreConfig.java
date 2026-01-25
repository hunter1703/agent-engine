package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

import java.util.Map;

@JSONType(
    typeKey = "type",
    seeAlso = { InMemoryMessageStoreConfig.class }
)
@BsonDiscriminator(key = "type")
public abstract class MessageStoreConfig implements Config {

  private String type;

  protected MessageStoreConfig(final MessageStoreType messageStoreType) {
    this.type = messageStoreType.name().toLowerCase();
  }

  @Override
  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  public enum MessageStoreType {
    MEMORY
  }
}