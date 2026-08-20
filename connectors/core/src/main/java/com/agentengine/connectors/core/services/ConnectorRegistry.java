package com.agentengine.connectors.core.services;

import com.agentengine.connectors.http.auth.HeaderAuthDecoratorSpec;
import com.agentengine.connectors.http.beans.HttpExecutorSpec;
import com.agentengine.connectors.infra.auth.AuthDecoratorSpec;
import com.agentengine.connectors.infra.beans.Application;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.connectors.infra.beans.ConnectorSpec;
import com.agentengine.connectors.infra.beans.ExecutorSpec;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.ResourceUtils;
import com.agentengine.util.common.StringUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.NamedType;
import jakarta.inject.Singleton;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Singleton
public final class ConnectorRegistry {
  private static final String CONNECTORS_DIRECTORY = "connectors";
  private static final String APP_CONFIG_FILE_NAME = "app.json";
  private static final ObjectMapper CONNECTOR_MAPPER = buildConnectorMapper();

  private final ConcurrentMap<String, Connector> connectorCache = new ConcurrentHashMap<>();

  public Connector get(final String appName, final String connectorName) {
    return connectorCache.computeIfAbsent(
        appName + ":" + connectorName, _ -> load(appName, connectorName));
  }

  private static ObjectMapper buildConnectorMapper() {
    final ObjectMapper mapper = JsonUtils.copyMapper();
    mapper.registerSubtypes(new NamedType(HttpExecutorSpec.class, ExecutorSpec.Type.HTTP.name()));
    mapper.registerSubtypes(
        new NamedType(HeaderAuthDecoratorSpec.class, AuthDecoratorSpec.Type.HEADER.name()));
    return mapper;
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
      final Connector connector = CONNECTOR_MAPPER.readValue(connectorContent, Connector.class);
      final ConnectorSpec mergedSpec = connector.spec().mergeWith(readAppSpec(appContent));
      return new Connector(
          connector.name(), connector.description(), connector.inputSchema(), mergedSpec);
    } catch (Exception e) {
      return null;
    }
  }

  private ConnectorSpec readAppSpec(final String appContent) throws Exception {
    if (StringUtils.isBlank(appContent)) {
      return null;
    }
    return CONNECTOR_MAPPER.readValue(appContent, Application.class).spec();
  }
}
