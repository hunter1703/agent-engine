package com.agentengine.engine.api.tools;

import com.agentengine.util.common.CollectionUtils;
import java.util.List;
import java.util.Map;

public record ToolDescriptor(
    String name,
    String description,
    Map<String, Object> configsSchema,
    String riskLevel) {
  public ToolDescriptor {
    configsSchema = CollectionUtils.nullSafeMap(configsSchema);
    riskLevel = riskLevel == null || riskLevel.isBlank() ? ToolRiskLevel.UNKNOWN.name() : riskLevel;
  }

  public ToolDescriptor(
      final String name,
      final String description,
      final Map<String, Object> configsSchema) {
    this(name, description, configsSchema, ToolRiskLevel.UNKNOWN.name());
  }

  public ToolDescriptor(
      final String name,
      final String description,
      final Map<String, Object> configsSchema,
      final ToolRiskLevel riskLevel) {
    this(name, description, configsSchema, riskLevel == null ? null : riskLevel.name());
  }

  public ToolRiskLevel riskLevelEnum() {
    return ToolRiskLevel.valueOfOrDefault(riskLevel);
  }
}
