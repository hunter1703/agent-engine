package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.core.ConnectionRepository;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import io.quarkus.arc.Unremovable;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class ConnectionServiceImpl implements ConnectionService {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectionServiceImpl.class);

  private final ConnectionRepository connectionRepository;
  private final ConnectorRegistry connectorRegistry;
  private final Instance<ConnectorService> connectorService;

  @Inject
  public ConnectionServiceImpl(
      ConnectionRepository connectionRepository,
      ConnectorRegistry connectorRegistry,
      Instance<ConnectorService> connectorService) {
    this.connectionRepository = connectionRepository;
    this.connectorRegistry = connectorRegistry;
    this.connectorService = connectorService;
  }

  @Override
  public Connection saveConnection(Connection connection) {
    final ConnectionSpec spec = getConnectionSpec(connection.getAppName());
    final String authConnector = spec == null ? null : spec.authConnector();
    if (StringUtils.isNotEmpty(authConnector)) {
      LOG.info("Fetching credentials for connection using connector {}", spec.authConnector());
      try {
        ConnectorRequest request =
            new ConnectorRequest(
                connection.getAppName(),
                spec.authConnector(),
                null,
                Map.of(
                    ConnectorConstants.CONNECTION_INPUT,
                    CollectionUtils.nullSafeMap(connection.getInputs())));
        final ConnectorResult<?> connectorResult = connectorService.get().execute(request);
        //noinspection unchecked
        final Map<String, Object> result =
            (Map<String, Object>) CollectionUtils.getFirst(connectorResult.result());
        connection.setCredentials(CollectionUtils.nullSafeMap(result));
        connection.setExpiresAt(getCredentialsExpiry(result, spec, connection.getInputs()));
      } catch (Exception e) {
        LOG.error("Failed to fetch credentials for connection", e);
      }
    }
    return connectionRepository.insert(connection);
  }

  @Override
  public PaginatedResult<Connection> getConnections(String appName, Page page) {
    final Query query =
        new Query().withFilter(Filters.eq(Connection.FIELD_APP_NAME, appName)).withPage(page);
    return connectionRepository.findByQuery(query);
  }

  @Override
  public Connection getConnection(String id) {
    if (id == null) {
      return null;
    }
    return connectionRepository.findById(id);
  }

  @Override
  public ConnectionSpec getConnectionSpec(String appName) {
    return connectorRegistry.getConnectionSpec(appName);
  }

  @Override
  public Connection refreshIfNeeded(Connection connection) {
    if (connection == null
        || connection.getExpiresAt() == null
        || connection.getExpiresAt() > System.currentTimeMillis()) {
      return connection;
    }

    final ConnectionSpec spec = getConnectionSpec(connection.getAppName());
    final String refreshConnector = spec == null ? null : spec.refreshConnector();
    if (StringUtils.isBlank(refreshConnector)) {
      return connection;
    }
    LOG.info(
        "Refreshing credentials for connection {} using connector {}",
        connection.getId(),
        spec.refreshConnector());
    try {
      final Map<String, Object> inputs = new HashMap<>();
      inputs.put(
          ConnectorConstants.CONNECTION_INPUT, CollectionUtils.nullSafeMap(connection.getInputs()));
      inputs.put(
          ConnectorConstants.CREDENTIALS, CollectionUtils.nullSafeMap(connection.getCredentials()));
      final ConnectorRequest request =
          new ConnectorRequest(connection.getAppName(), spec.refreshConnector(), null, inputs);
      final ConnectorResult<?> connectorResult = connectorService.get().execute(request);
      //noinspection unchecked
      final Map<String, Object> result =
          (Map<String, Object>) CollectionUtils.getFirst(connectorResult.result());
      final Map<String, Object> credentials =
          CollectionUtils.nullSafeMutableMap(connection.getCredentials());
      credentials.putAll(CollectionUtils.nullSafeMap(result));
      connection.setCredentials(credentials);
      connection.setExpiresAt(getCredentialsExpiry(credentials, spec, inputs));
      return saveConnection(connection);
    } catch (Exception e) {
      LOG.error("Failed to refresh connection {}", connection.getId(), e);
    }
    return connection;
  }

  private static Long getCredentialsExpiry(
      final Map<String, Object> fetchedCredentials,
      final ConnectionSpec spec,
      final Map<String, Object> connectionInputs) {
    final Map<String, Object> contextParams =
        Map.of(
            ConnectorConstants.CONNECTION_INPUT,
            connectionInputs,
            ConnectorConstants.CREDENTIALS,
            fetchedCredentials);
    final Template<Object> expiryTemplate =
        TemplateUtils.buildStringTemplate(spec.credsExpiryFieldPathTemplate());
    Long expiry = parseExpiry(expiryTemplate.getValue(contextParams));
    final Template<String> expiryUnitTemplate =
        TemplateUtils.buildStringTemplate(spec.expiryUnit());
    final String expiryUnit = expiryUnitTemplate.getValue(contextParams);

    if (expiry == null) {
      final Template<Object> defaultExpiryTemplate =
          TemplateUtils.buildStringTemplate(spec.defaultExpiryTemplate());
      expiry = parseExpiry(defaultExpiryTemplate.getValue(contextParams));
    }
    return getAbsoluteExpiry(
        expiry, TimeUnit.valueOf(expiryUnit.toUpperCase(Locale.ROOT)), spec.expiryType());
  }

  private static Long parseExpiry(Object value) {
    switch (value) {
      case null -> {
        return null;
      }
      case Number number -> {
        return number.longValue();
      }
      case String str -> {
        try {
          return Long.parseLong(str);
        } catch (NumberFormatException e) {
          LOG.warn("Failed to parse expiry value: {}", str);
          return null;
        }
      }
      default -> {}
    }
    return null;
  }

  private static Long getAbsoluteExpiry(final Long expiry, final TimeUnit unit, final String type) {
    if (expiry == null) {
      return null;
    }
    if (ConnectorConstants.RELATIVE.equalsIgnoreCase(type)) {
      return System.currentTimeMillis() + unit.toMillis(expiry);
    } else {
      return unit.toMillis(expiry);
    }
  }
}
