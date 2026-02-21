package com.agentengine.engine.api.tools;

import java.util.Map;

public interface ToolAssetProvider {

  String agentId();

  String name();

  default Map<String, Object> configsSchema() {
    return Map.of();
  }
}
