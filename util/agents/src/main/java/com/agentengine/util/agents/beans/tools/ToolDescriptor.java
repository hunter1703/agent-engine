package com.agentengine.util.agents.beans.tools;

import java.util.Map;

public record ToolDescriptor(
    String name, String description, Map<String, Object> configsSchema, ToolRiskLevel riskLevel) {

  public ToolDescriptor(
      final String name, final String description, final Map<String, Object> configsSchema) {
    this(name, description, configsSchema, ToolRiskLevel.UNKNOWN);
  }

  public ToolDescriptor(final String name, final String description, ToolRiskLevel riskLevel) {
    this(name, description, null, riskLevel);
  }

  public ToolDescriptor(final String name, final String description) {
    this(name, description, null, ToolRiskLevel.UNKNOWN);
  }
}
