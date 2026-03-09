package com.agentengine.connectors.core;

import java.util.Map;

public final class NoopConnectorAuthMaterialProvider implements ConnectorAuthMaterialProvider {

  @Override
  public Map<String, Object> resolve(final String appName) {
    return Map.of();
  }
}
