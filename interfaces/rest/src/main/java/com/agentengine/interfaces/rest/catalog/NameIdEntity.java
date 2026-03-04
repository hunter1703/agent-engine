package com.agentengine.interfaces.rest.catalog;

public class NameIdEntity {
  private final String id;
  private final String name;

  public NameIdEntity(final String id, final String name) {
    this.id = id;
    this.name = name;
  }

  public String getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
