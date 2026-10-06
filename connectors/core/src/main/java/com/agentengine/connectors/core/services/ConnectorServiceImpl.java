package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.connectors.infra.builders.ConnectorExecutorFactory;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;

@Singleton
@Unremovable
public class ConnectorServiceImpl implements ConnectorService {
  private final ConnectorRegistry registry;
  private final ConnectorExecutorFactory executorFactory;

  @Inject
  public ConnectorServiceImpl(
      ConnectorRegistry registry, ConnectorExecutorFactory executorFactory) {
    this.registry = registry;
    this.executorFactory = executorFactory;
  }

  @Override
  public <T> ConnectorResult<T> execute(ConnectorRequest request) throws ConnectorException {
    final Connector connector = getConnector(request.appName(), request.connectorName());
    final ConnectorExecutor<Map<String, Object>, T> executor =
        executorFactory.build(request.connection(), connector.spec());
    return executor.execute(request.connection(), request.input());
  }

  @Override
  public ConnectorMetadata describe(final String appName, final String connectorName)
      throws ConnectorException {
    final Connector connector = getConnector(appName, connectorName);
    return new ConnectorMetadata(
        appName, connectorName, connector.description(), connector.inputSchema());
  }

  @Override
  public List<String> findAllConnectorAppNames() {
    return registry.listApps();
  }

  @Override
  public List<String> findAllConnectorNames(String appName) {
    return registry.listConnectors(appName);
  }

  private Connector getConnector(final String appName, final String connectorName)
      throws ConnectorException {
    final Connector connector = registry.get(appName, connectorName);
    if (connector == null) {
      throw new ConnectorException(
          "Connector not found for app: " + appName + ", connector: " + connectorName);
    }
    return connector;
  }
}
