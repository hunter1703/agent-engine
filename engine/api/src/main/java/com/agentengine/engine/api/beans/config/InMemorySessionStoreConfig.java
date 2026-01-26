package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "memory")
@BsonDiscriminator(value = "memory")
public class InMemorySessionStoreConfig extends SessionStoreConfig {

  public InMemorySessionStoreConfig() {
    super(SessionStoreType.MEMORY);
  }
}
