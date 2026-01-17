package com.agentengine.engine.beans.config;

import com.alibaba.fastjson2.annotation.JSONType;

@JSONType(typeName = "memory")
public class MemoryStateStoreConfig extends StateStoreConfig {
  public MemoryStateStoreConfig() {
    super(StateStoreType.MEMORY);
  }
}
