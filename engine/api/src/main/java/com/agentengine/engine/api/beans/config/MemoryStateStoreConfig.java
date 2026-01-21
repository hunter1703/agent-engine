package com.agentengine.engine.api.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@JSONType(typeName = "memory")
@BsonDiscriminator(value = "memory")
public class MemoryStateStoreConfig extends StateStoreConfig {
  public MemoryStateStoreConfig() {
    super(StateStoreType.MEMORY);
  }
}
