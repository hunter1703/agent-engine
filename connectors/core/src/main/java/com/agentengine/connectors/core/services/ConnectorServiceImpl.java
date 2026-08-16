package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.exceptions.ConnectorException;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.core.factories.ConnectorExecutorFactory;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.connectors.infra.executor.ConnectorExecutor;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
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
    final Connector connector = registry.get(request.name());
    if (connector == null) {
      throw new ConnectorException("Connector not found: " + request.name());
    }

    final ConnectorExecutor<Map<String, Object>, T> executor =
        executorFactory.build(connector.spec());
    return executor.execute(request.input());
  }
}
