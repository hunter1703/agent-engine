package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.AuthSpec;
import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.beans.CredentialsConfig;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.connectors.api.services.ConnectionCacheTag;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.core.ConnectionRepository;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.SchemaUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.distributed.DistributedLockManager;
import com.agentengine.util.scripts.TemplateUtils;
import com.agentengine.util.scripts.templated.Template;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConnectionServiceImpl implements ConnectionService {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectionServiceImpl.class);

  private final ConnectionRepository connectionRepository;
  private final ConnectorRegistry connectorRegistry;
  private final ConnectorService connectorService;
  private final DistributedLockManager distributedLockManager;
  private final DistributedCacheManager distributedCacheManager;
  private final EncryptionService encryptionService;

  @Inject
  public ConnectionServiceImpl(
      ConnectionRepository connectionRepository,
      ConnectorRegistry connectorRegistry,
      ConnectorService connectorService,
      DistributedLockManager distributedLockManager,
      DistributedCacheManager distributedCacheManager,
      EncryptionService encryptionService) {
    this.connectionRepository = connectionRepository;
    this.connectorRegistry = connectorRegistry;
    this.connectorService = connectorService;
    this.distributedLockManager = distributedLockManager;
    this.distributedCacheManager = distributedCacheManager;
    this.encryptionService = encryptionService;
  }

  @Override
  public <T> ConnectorResult<T> executeConnectorRequest(ConnectorRequest request) {
    final Connection connection = getDecryptedConnection(request.connectionId());
    return connectorService.execute(
        new ConnectorRequest(
            request.appName(),
            request.connectorName(),
            request.connectionId(),
            connection,
            request.input()));
  }

  @Override
  public Connection saveConnection(Connection connection) {
    final ConnectionSpec spec = getConnectionSpec(connection.getAppName());
    final AuthSpec authConfig =
        CollectionUtils.getValueFromMap(spec.authConfigs(), connection.getAuthType());
    final String authConnector =
        authConfig == null || authConfig.fetch() == null
            ? null
            : authConfig.fetch().connectorName();
    if (StringUtils.isNotEmpty(authConnector)) {
      LOG.info("Fetching credentials for connection using connector {}", authConnector);
      try {
        final ConnectorRequest request =
            new ConnectorRequest(
                connection.getAppName(),
                authConnector,
                null,
                null,
                Map.of(
                    ConnectorConstants.CONNECTION_INPUT,
                    CollectionUtils.nullSafeMap(connection.getInputs())));
        final ConnectorResult<?> connectorResult = connectorService.execute(request);
        //noinspection unchecked
        final Map<String, Object> result =
            (Map<String, Object>) CollectionUtils.getFirst(connectorResult.result());
        connection.setCredentials(CollectionUtils.nullSafeMap(result));
        connection.setExpiresAt(
            getCredentialsExpiry(result, authConfig.fetch(), connection.getInputs()));
      } catch (Exception e) {
        LOG.error("Failed to fetch credentials for connection", e);
      }
    }
    encryptSensitiveInputs(connection);
    final Connection saved = connectionRepository.save(connection);
    distributedCacheManager.broadcastInvalidation(
        ConnectionCacheTag.CONNECTIONS.name(), saved.getAppName());
    return saved;
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
  public Connection getDecryptedConnection(String id) {
    if (id == null) {
      return null;
    }
    final Connection connection = connectionRepository.findById(id);
    decryptSensitiveInputs(connection);
    return connection;
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
    final AuthSpec authConfig =
        CollectionUtils.getValueFromMap(spec.authConfigs(), connection.getAuthType());
    final String refreshConnector =
        authConfig == null || authConfig.refresh() == null
            ? null
            : authConfig.refresh().connectorName();
    if (StringUtils.isBlank(refreshConnector)) {
      return connection;
    }

    final Lock lock = distributedLockManager.getLock("connection_refresh_" + connection.getId());
    boolean acquired = false;
    long startTime = System.currentTimeMillis();
    long maxWaitTimeMillis = TimeUnit.SECONDS.toMillis(120);

    while ((System.currentTimeMillis() - startTime) <= maxWaitTimeMillis) {
      try {
        acquired = lock.tryLock(10, TimeUnit.SECONDS);
        if (acquired) {
          break;
        } else {
          Connection connectionFromDB = getDecryptedConnection(connection.getId());
          if (connectionFromDB != null
              && connectionFromDB.getExpiresAt() != null
              && connectionFromDB.getExpiresAt() > System.currentTimeMillis()) {
            return connectionFromDB;
          }
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new RuntimeException("Interrupted while waiting for lock", e);
      }
    }

    if (!acquired) {
      throw new RuntimeException("Failed to refresh credentials, waited for 2 minutes");
    }

    try {
      Connection connectionFromDB = getDecryptedConnection(connection.getId());
      if (connectionFromDB != null
          && connectionFromDB.getExpiresAt() != null
          && connectionFromDB.getExpiresAt() > System.currentTimeMillis()) {
        return connectionFromDB;
      }

      LOG.info(
          "Refreshing credentials for connection {} using connector {}",
          connection.getId(),
          refreshConnector);

      if (connectionFromDB == null) {
        return null;
      }
      try {
        final Map<String, Object> inputs = new HashMap<>();
        inputs.put(
            ConnectorConstants.CONNECTION_INPUT,
            CollectionUtils.nullSafeMap(connectionFromDB.getInputs()));
        inputs.put(
            ConnectorConstants.CREDENTIALS,
            CollectionUtils.nullSafeMap(connectionFromDB.getCredentials()));
        final ConnectorRequest request =
            new ConnectorRequest(
                connectionFromDB.getAppName(), refreshConnector, null, connectionFromDB, inputs);
        final ConnectorResult<?> connectorResult = connectorService.execute(request);
        //noinspection unchecked
        final Map<String, Object> result =
            (Map<String, Object>) CollectionUtils.getFirst(connectorResult.result());
        final Map<String, Object> credentials =
            CollectionUtils.nullSafeMutableMap(connectionFromDB.getCredentials());
        credentials.putAll(CollectionUtils.nullSafeMap(result));
        connectionFromDB.setCredentials(credentials);
        connectionFromDB.setExpiresAt(
            getCredentialsExpiry(credentials, authConfig.refresh(), inputs));

        encryptSensitiveInputs(connectionFromDB);
        Connection saved = connectionRepository.save(connectionFromDB);
        distributedCacheManager.broadcastInvalidation(
            ConnectionCacheTag.CONNECTIONS.name(), saved.getAppName());
        return saved;
      } catch (Exception e) {
        LOG.error("Failed to refresh connection {}", connection.getId(), e);
      }
      return connectionFromDB;
    } finally {
      lock.unlock();
    }
  }

  @Override
  public ConnectorMetadata getConnectorMetadata(String appName, String connectorName) {
    final Connector connector = connectorRegistry.get(appName, connectorName);
    if (connector == null) {
      return null;
    }
    return new ConnectorMetadata(
        appName, connectorName, connector.description(), connector.inputSchema());
  }

  private void encryptSensitiveInputs(Connection connection) {
    if (CollectionUtils.isEmpty(connection.getInputs())
        || !encryptionService.isEncryptionEnabled()) {
      return;
    }
    final ConnectionSpec spec = getConnectionSpec(connection.getAppName());
    if (spec == null || CollectionUtils.isEmpty(spec.schema())) {
      return;
    }

    @SuppressWarnings("unchecked")
    final Map<String, Object> newInputs =
        (Map<String, Object>)
            SchemaUtils.walk(
                spec.schema(),
                connection.getInputs(),
                (_, schemaNode, dataNode) -> {
                  if (Boolean.TRUE.equals(
                          CollectionUtils.getBooleanValueFromMap(schemaNode, "sensitive"))
                      && dataNode instanceof String strData) {
                    return encryptionService.encrypt(strData);
                  }
                  return dataNode;
                });
    connection.setInputs(newInputs);
  }

  private void decryptSensitiveInputs(Connection connection) {
    if (connection.getInputs() == null || !encryptionService.isEncryptionEnabled()) return;

    @SuppressWarnings("unchecked")
    Map<String, Object> newInputs =
        (Map<String, Object>)
            CollectionUtils.walk(
                connection.getInputs(),
                (_, dataNode) -> {
                  if (dataNode instanceof String str && encryptionService.isEncrypted(str)) {
                    return encryptionService.decrypt(str);
                  }
                  return dataNode;
                });
    connection.setInputs(newInputs);
  }

  private static Long getCredentialsExpiry(
      final Map<String, Object> fetchedCredentials,
      final CredentialsConfig credentialsConfig,
      final Map<String, Object> connectionInputs) {
    final Map<String, Object> contextParams =
        Map.of(
            ConnectorConstants.CONNECTION_INPUT,
            connectionInputs,
            ConnectorConstants.CREDENTIALS,
            fetchedCredentials);
    final Template<Object> expiryTemplate =
        TemplateUtils.buildStringTemplate(credentialsConfig.credsExpiryFieldPathTemplate());
    Long expiry = parseExpiry(expiryTemplate.getValue(contextParams));
    final String expiryUnit = credentialsConfig.expiryUnit();

    if (expiry == null) {
      final Template<Object> defaultExpiryTemplate =
          TemplateUtils.buildStringTemplate(credentialsConfig.defaultExpiryTemplate());
      expiry = parseExpiry(defaultExpiryTemplate.getValue(contextParams));
    }
    return getAbsoluteExpiry(
        expiry,
        TimeUnit.valueOf(expiryUnit.toUpperCase(Locale.ROOT)),
        credentialsConfig.expiryType());
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
