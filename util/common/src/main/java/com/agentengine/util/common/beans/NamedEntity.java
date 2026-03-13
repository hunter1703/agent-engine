package com.agentengine.util.common.beans;

public class NamedEntity extends BaseEntity {
  private String name;

  public NamedEntity() {
  }

  public NamedEntity(String id, String name) {
    super(id);
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }
}
