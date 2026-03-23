package com.agentengine.util.agents.tools;

import java.util.Map;

public record ToolDescriptor(String name, String description, Map<String, Object> configsSchema, ToolRiskLevel riskLevel) {

  public ToolDescriptor(final String name, final String description, final Map<String, Object> configsSchema) {
    this(name, description, configsSchema, ToolRiskLevel.UNKNOWN);
  }
}
