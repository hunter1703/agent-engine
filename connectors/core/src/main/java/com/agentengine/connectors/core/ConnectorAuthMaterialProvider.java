package com.agentengine.connectors.core;

import java.util.Map;

public interface ConnectorAuthMaterialProvider {

    Map<String, Object> resolve(String appName);
}
