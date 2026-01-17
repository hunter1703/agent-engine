package com.agentengine.engine.beans.config;

public abstract class StateStoreConfig implements Config {
  private String type;

  protected StateStoreConfig(final StateStoreType stateStoreType) {
    this.type = stateStoreType.name().toLowerCase();
  }

  public String getType() {
    return type;
  }

  public void setType(final String type) {
    this.type = type;
  }

  protected enum StateStoreType {
    MEMORY,
    MONGO
  }
}
