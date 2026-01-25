package com.agentengine.engine.api.beans.config;

public class InMemoryMessageStoreConfig extends MessageStoreConfig {

  public InMemoryMessageStoreConfig() {
    super(MessageStoreType.MEMORY);
  }
}