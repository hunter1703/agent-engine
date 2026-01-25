package com.agentengine.engine.api.beans.config;

public class InMemorySessionStoreConfig extends SessionStoreConfig {

  public InMemorySessionStoreConfig() {
    super(SessionStoreType.MEMORY);
  }
}