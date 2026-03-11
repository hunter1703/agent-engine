package com.agentengine.engine.api.tools;

import com.agentengine.util.common.CollectionUtils;
import java.util.List;
import java.util.Map;

public record ToolDescriptor(
    String name,
    String description,
    List<String> agentIds,
    Map<String, Object> configsSchema,
    ToolRiskLevel riskLevel) {
  public ToolDescriptor {
    agentIds = CollectionUtils.nullSafeList(agentIds);
    configsSchema = CollectionUtils.nullSafeMap(configsSchema);
    riskLevel = riskLevel == null ? ToolRiskLevel.UNKNOWN : riskLevel;
  }

  public ToolDescriptor(
      final String name,
      final String description,
      final List<String> agentIds,
      final Map<String, Object> configsSchema) {
    this(name, description, agentIds, configsSchema, ToolRiskLevel.UNKNOWN);
  }
}
