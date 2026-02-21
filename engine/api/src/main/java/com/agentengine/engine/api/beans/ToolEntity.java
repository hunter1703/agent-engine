package com.agentengine.engine.api.beans;

import java.util.Map;

public class ToolEntity extends BaseEntity {
  private String name;
  private String description;
  private Map<String, Object> configsSchema;

  public ToolEntity() {}

  public ToolEntity(String id, String name, String description, Map<String, Object> configsSchema) {
    this.setId(id);
    this.name = name;
    this.description = description;
    this.configsSchema = configsSchema;
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

  public Map<String, Object> getConfigsSchema() {
    return configsSchema;
  }

  public void setConfigsSchema(Map<String, Object> configsSchema) {
    this.configsSchema = configsSchema;
  }
}
