package com.agentengine.engine.api.tools;

import com.agentengine.engine.api.utils.CollectionUtils;
import java.util.List;
import java.util.Map;

public record ToolDescriptor(String name, List<String> agentIds, Map<String, Object> configsSchema) {
  public ToolDescriptor {
    agentIds = CollectionUtils.nullSafeList(agentIds);
    configsSchema = CollectionUtils.nullSafeMap(configsSchema);
  }
}
