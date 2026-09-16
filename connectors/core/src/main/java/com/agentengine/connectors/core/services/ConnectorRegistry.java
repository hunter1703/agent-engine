package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.infra.beans.Application;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.util.common.JsonCodec;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.SimpleJsonCodec;
import com.agentengine.util.common.StringUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public final class ConnectorRegistry {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectorRegistry.class);
  private static final String CONNECTORS_DIRECTORY = "connectors";
  private static final String APP_CONFIG_FILE_NAME = "app.json";

  private final ConcurrentMap<String, Connector> connectorCache = new ConcurrentHashMap<>();
  private final JsonCodec jsonCodec;

  @Inject
  public ConnectorRegistry(final SimpleJsonCodec jsonCodec) {
    this.jsonCodec = jsonCodec;
  }

  public Connector get(final String appName, final String connectorName) {
    return connectorCache.computeIfAbsent(
        appName + ":" + connectorName, _ -> load(appName, connectorName));
  }

  private Connector load(final String appName, final String connectorName) {
    final String connectorPath =
        "/%s/%s/%s.json".formatted(CONNECTORS_DIRECTORY, appName, connectorName);
    final String connectorContent = ResourceUtils.loadResourceAsString(connectorPath);
    if (StringUtils.isBlank(connectorContent)) {
      return null;
    }

    Connector connector = jsonCodec.deserialize(connectorContent, Connector.class);

    if (connector == null) {
      return null;
    }

    final String appPath =
        "/%s/%s/%s".formatted(CONNECTORS_DIRECTORY, appName, APP_CONFIG_FILE_NAME);
    final String appContent = ResourceUtils.loadResourceAsString(appPath);
    final ConnectorSpec appSpec = readAppSpec(appContent);
    final ConnectorSpec connectorSpec =
        connector.spec().mergeWith(appSpec, connector.authResource());
    return new Connector(
        connector.name(),
        connector.description(),
        connector.inputSchema(),
        connectorSpec,
        connector.authResource());
  }

  public ConnectionSpec getConnectionSpec(final String appName) {
    final String appContent =
        ResourceUtils.loadResourceAsString(
            "/%s/%s/%s".formatted(CONNECTORS_DIRECTORY, appName, APP_CONFIG_FILE_NAME));
    if (StringUtils.isBlank(appContent)) {
      return null;
    }
    try {
      Application app = jsonCodec.deserialize(appContent, Application.class);
      return app != null ? app.connection() : null;
    } catch (Exception e) {
      return null;
    }
  }

  private ConnectorSpec readAppSpec(final String appContent) {
    if (StringUtils.isBlank(appContent)) {
      return null;
    }
    try {
      return jsonCodec.deserialize(appContent, Application.class).connector();
    } catch (Exception e) {
      return null;
    }
  }
}
