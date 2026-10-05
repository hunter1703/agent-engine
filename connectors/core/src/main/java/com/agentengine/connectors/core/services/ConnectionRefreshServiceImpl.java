package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.AuthSpec;
import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.connectors.api.services.ConnectionRefresher;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.core.ConnectionRepository;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.distributed.DistributedLockManager;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ConnectionRefreshServiceImpl implements ConnectionRefresher {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectionRefreshServiceImpl.class);

  private final ConnectionRepository connectionRepository;
  private final ConnectorRegistry connectorRegistry;
  private final ConnectorService connectorService;
  private final DistributedLockManager distributedLockManager;
  private final EncryptionService encryptionService;

  @Inject
  public ConnectionRefreshServiceImpl(
      ConnectionRepository connectionRepository,
      ConnectorRegistry connectorRegistry,
      ConnectorService connectorService,
      DistributedLockManager distributedLockManager,
      EncryptionService encryptionService) {
    this.connectionRepository = connectionRepository;
    this.connectorRegistry = connectorRegistry;
    this.connectorService = connectorService;
    this.distributedLockManager = distributedLockManager;
    this.encryptionService = encryptionService;
  }

  @Override
  public Connection refreshIfNeeded(Connection connection) {
    if (connection == null
        || connection.getExpiresAt() == null
        || connection.getExpiresAt() > System.currentTimeMillis()) {
      return connection;
    }

    final ConnectionSpec spec = connectorRegistry.getConnectionSpec(connection.getAppName());
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
            ConnectionUtils.getCredentialsExpiry(credentials, authConfig.refresh(), inputs));

        ConnectionUtils.encryptSensitiveInputs(connectionFromDB, spec, encryptionService);
        return connectionRepository.save(connectionFromDB);
      } catch (Exception e) {
        LOG.error("Failed to refresh connection {}", connection.getId(), e);
      }
      return connectionFromDB;
    } finally {
      lock.unlock();
    }
  }

  private Connection getDecryptedConnection(String id) {
    if (id == null) {
      return null;
    }
    final Connection connection = connectionRepository.findById(id);
    if (connection != null) {
      ConnectionUtils.decryptSensitiveInputs(connection, encryptionService);
    }
    return connection;
  }
}
