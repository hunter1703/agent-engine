package com.agentengine.engine.api.beans;

public class ToolEntity extends BaseEntity {
  private String name;
  private String description;

  public ToolEntity() {
  }

  public ToolEntity(String id, String name, String description) {
    this.setId(id);
    this.name = name;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }
}
