package com.agentengine.api;

import java.util.Map;

public class ToolCallDto {
  private String id;
  private String name;
  private Map<String, Object> args;

  public ToolCallDto() {}

  public ToolCallDto(final String id, final String name, final Map<String, Object> args) {
    this.id = id;
    this.name = name;
    this.args = args;
  }

  public String getId() {
    return id;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public Map<String, Object> getArgs() {
    return args;
  }

  public void setArgs(final Map<String, Object> args) {
    this.args = args;
  }
}
