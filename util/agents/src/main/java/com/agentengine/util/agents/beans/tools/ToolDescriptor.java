package com.agentengine.util.agents.beans.tools;

import com.agentengine.util.agents.builder.UILayout;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;

public record ToolDescriptor(
    String name,
    String description,
    ToolRiskLevel riskLevel,
    Map<String, Object> configsSchema,
    UILayout configsLayout) {

  public ToolDescriptor(
      final String name,
      final String description,
      final Map<String, Object> configsSchema,
      final ToolRiskLevel riskLevel) {
    this(name, description, riskLevel, configsSchema, null);
  }

  public ToolDescriptor(
      final String name,
      final String description,
      final Map<String, Object> configsSchema,
      final UILayout configsLayout) {
    this(name, description, ToolRiskLevel.UNKNOWN, configsSchema, configsLayout);
  }

  public ToolDescriptor(
      final String name, final String description, final Map<String, Object> configsSchema) {
    this(name, description, ToolRiskLevel.UNKNOWN, configsSchema, null);
  }

  public ToolDescriptor(final String name, final String description, ToolRiskLevel riskLevel) {
    this(name, description, riskLevel, null, null);
  }

  public ToolDescriptor(final String name, final String description) {
    this(name, description, ToolRiskLevel.UNKNOWN, null, null);
  }

  @JsonProperty("id")
  public String id() {
    return name;
  }
}
