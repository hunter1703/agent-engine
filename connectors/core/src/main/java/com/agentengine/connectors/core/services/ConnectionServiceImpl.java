package com.agentengine.connectors.core.services;

import com.agentengine.connectors.api.beans.AuthSpec;
import com.agentengine.connectors.api.beans.Connection;
import com.agentengine.connectors.api.beans.ConnectionSpec;
import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.beans.ConnectorRequest;
import com.agentengine.connectors.api.beans.ConnectorResult;
import com.agentengine.connectors.api.constants.ConnectorConstants;
import com.agentengine.connectors.api.services.ConnectionRefresher;
import com.agentengine.connectors.api.services.ConnectionService;
import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.connectors.core.ConnectionRepository;
import com.agentengine.connectors.infra.beans.Connector;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.crypto.EncryptionService;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.agentengine.util.distributed.DistributedLockManager;
import com.agentengine.util.tenancy.Permission;
import com.agentengine.util.tenancy.PermissionChecker;
import com.google.common.cache.CacheBuilder;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
@Unremovable
public class ConnectionServiceImpl implements ConnectionService {
  private static final Logger LOG = LoggerFactory.getLogger(ConnectionServiceImpl.class);
  public static final String CACHE_NAME = "connection-cache";

  private final ConnectionRepository connectionRepository;
  private final ConnectorRegistry connectorRegistry;
  private final ConnectorService connectorService;
  private final DistributedLockManager distributedLockManager;
  private final EncryptionService encryptionService;
  private final PermissionChecker permissionChecker;
  private final DistributedCache<Connection> connectionCache;
  private final ConnectionRefresher connectionRefresher;

  @Inject
  public ConnectionServiceImpl(
      ConnectionRepository connectionRepository,
      ConnectorRegistry connectorRegistry,
      ConnectorService connectorService,
      DistributedLockManager distributedLockManager,
      EncryptionService encryptionService,
      PermissionChecker permissionChecker,
      DistributedCacheManager distributedCacheManager,
      ConnectionRefresher connectionRefresher) {
    this.connectionRepository = connectionRepository;
    this.connectorRegistry = connectorRegistry;
    this.connectorService = connectorService;
    this.distributedLockManager = distributedLockManager;
    this.encryptionService = encryptionService;
    this.permissionChecker = permissionChecker;
    this.connectionRefresher = connectionRefresher;
    this.connectionCache =
        new DistributedCache.Builder<Connection>(CACHE_NAME, distributedCacheManager)
            .localCache(CacheBuilder.newBuilder().maximumSize(1000))
            .scope(CacheScope.CUSTOMER)
            .loader(this::getDecryptedConnection)
            .build();
  }

  @Override
  public <T> ConnectorResult<T> executeConnectorRequest(ConnectorRequest request) {
    Connection connection = getDecryptedConnectionFromCache(request.connectionId());
    return connectorService.execute(
        new ConnectorRequest(
            request.appName(),
            request.connectorName(),
            request.connectionId(),
            connection,
            request.input()));
  }

  @Override
  public Connection saveConnection(Connection connection, boolean skipVersion) {
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
            ConnectionUtils.getCredentialsExpiry(
                result, authConfig.fetch(), connection.getInputs()));
      } catch (Exception e) {
        LOG.error("Failed to fetch credentials for connection", e);
      }
    }
    ConnectionUtils.encryptSensitiveInputs(connection, spec, encryptionService);
    return skipVersion
        ? connectionRepository.saveIgnoringVersion(connection)
        : connectionRepository.save(connection);
  }

  @Override
  public PaginatedResult<Connection> getConnections(
      final String appName,
      final Page page,
      final List<String> includeFields,
      final List<String> excludeFields) {
    final Query query =
        new Query()
            .withFilter(Filters.eq(Connection.FIELD_APP_NAME, appName))
            .withPage(page)
            .withIncludeFields(includeFields)
            .withExcludeFields(excludeFields);
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
    ConnectionUtils.decryptSensitiveInputs(connection, encryptionService);
    return connection;
  }

  @Override
  public ConnectionSpec getConnectionSpec(String appName) {
    return connectorRegistry.getConnectionSpec(appName);
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

  private Connection getDecryptedConnectionFromCache(String id) {
    if (id == null) {
      return null;
    }
    final Connection connection = connectionCache.get(id);
    if (!permissionChecker.hasPermission(
        () -> connection, AssetClass.CONNECTION, Permission.READ)) {
      throw new UnauthorizedException(
          "User does not have permission to access connection with id: " + id);
    }
    return connection;
  }
}
