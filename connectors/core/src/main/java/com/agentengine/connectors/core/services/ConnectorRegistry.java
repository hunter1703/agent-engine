package com.agentengine.connectors.core.services;

import com.agentengine.connectors.infra.beans.Application;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public final class ConnectorRegistry {
  private static final String CONNECTORS_DIRECTORY = "connectors";
  private static final String APP_CONFIG_FILE_NAME = "app.json";

  private final ConcurrentMap<String, Connector> connectorCache = new ConcurrentHashMap<>();
  private final JsonCodec jsonCodec;

  @Inject
  public ConnectorRegistry(final JsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
  }

  public Connector get(final String appName, final String connectorName) {
    return connectorCache.computeIfAbsent(
        appName + ":" + connectorName, _ -> load(appName, connectorName));
  }

  private Connector load(final String appName, final String connectorName) {
    final String connectorContent =
        ResourceUtils.loadResourceAsString(
            "/%s/%s/%s.json".formatted(CONNECTORS_DIRECTORY, appName, connectorName));
    if (StringUtils.isBlank(connectorContent)) {
      return null;
    }
    final String appContent =
        ResourceUtils.loadResourceAsString(
            "/%s/%s/%s".formatted(CONNECTORS_DIRECTORY, appName, APP_CONFIG_FILE_NAME));

    try {
      final Connector connector = jsonCodec.deserialize(connectorContent, Connector.class);
      final ConnectorSpec mergedSpec = connector.spec().mergeWith(readAppSpec(appContent));
      return new Connector(
          connector.name(), connector.description(), connector.inputSchema(), mergedSpec);
    } catch (Exception e) {
      return null;
    }
  }

  private ConnectorSpec readAppSpec(final String appContent) {
    if (StringUtils.isBlank(appContent)) {
      return null;
    }
    return jsonCodec.deserialize(appContent, Application.class).spec();
  }
}
