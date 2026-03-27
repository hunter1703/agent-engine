package com.agentengine.connectors.core;

import com.agentengine.connectors.core.runtime.Connection;
import com.agentengine.connectors.core.config.ConnectorDefinition;
import com.agentengine.connectors.core.runtime.ConnectorExecutionResult;
import com.agentengine.connectors.core.runtime.ConnectorExecutor;
import com.agentengine.connectors.core.runtime.RequestContext;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

@Singleton
public class DefaultConnectorService implements ConnectorService {
  private final ConnectorRegistry registry;
  private final ConnectorExecutor executor;
  private final ConnectorAuthMaterialProvider authMaterialProvider;

  @Inject
  public DefaultConnectorService(ConnectorRegistry registry, ConnectorExecutor executor,
      ConnectorAuthMaterialProvider authMaterialProvider) {
    this.registry = registry;
    this.executor = executor;
    this.authMaterialProvider = authMaterialProvider;
  }

  @Override
  public ConnectorExecutionResult execute(String connectorId, Map<String, Object> input) {
    final ConnectorDefinition definition = registry.getConnector(connectorId)
        .orElseThrow(() -> new IllegalArgumentException("Unknown connector: " + connectorId));
    final Map<String, Object> safeInput = input == null ? Map.of() : Map.copyOf(input);
    final Map<String, Object> authMaterials = authMaterialProvider.resolve(definition.appName());
    final Map<String, Object> mergedInput = mergeInputWithAuth(safeInput, authMaterials);
    final Connection connection = new Connection(definition.appName(), authMaterials);
    return executor.executeOnce(definition, new RequestContext(mergedInput, null, authMaterials, connection, null, null));
  }

  private static Map<String, Object> mergeInputWithAuth(final Map<String, Object> input, final Map<String, Object> authMaterials) {
    if (authMaterials == null || authMaterials.isEmpty()) {
      return input;
    }
    final Map<String, Object> mergedInput = new LinkedHashMap<>(input);
    final Object currentAuth = input.get("auth");
    final Map<String, Object> mergedAuth = new LinkedHashMap<>();
    if (currentAuth instanceof Map<?, ?> authMap) {
      authMap.forEach((key, value) -> mergedAuth.put(String.valueOf(key), value));
    }
    mergedAuth.putAll(authMaterials);
    mergedInput.put("auth", Map.copyOf(mergedAuth));
    return Map.copyOf(mergedInput);
  }
}
