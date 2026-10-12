package com.agentengine.util.models;

public abstract class ConfiguredModel<T> {

  private final T model;

  protected ConfiguredModel(final T model) {
    this.model = model;
  }

  public T model() {
    return model;
  }
}
