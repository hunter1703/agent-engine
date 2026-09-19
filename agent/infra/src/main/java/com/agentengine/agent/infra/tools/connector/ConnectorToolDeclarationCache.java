package com.agentengine.agent.infra.tools.connector;

import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.services.ConnectionCacheTag;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.distributed.CacheScope;
import com.agentengine.util.distributed.DistributedCache;
import com.agentengine.util.distributed.DistributedCacheManager;
import com.google.common.cache.CacheBuilder;
import com.google.genai.types.FunctionDeclaration;
import com.google.genai.types.Schema;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Caches each connector's tool {@link FunctionDeclaration} - the "input"/"connectionId" wrapper
 * schema a {@link ConnectorTool} exposes to the model. Scoped per user, not just per customer, via
 * {@link CacheScope#USER} - a connector's connection-id enum can eventually differ per
 * user once connections carry RBAC, so one user's cached declaration must never leak into
 * another's.
 *
 * <p>Tagged {@link ConnectionCacheTag#CONNECTIONS} alongside {@code ConnectorCacheService}'s own
 * connection-id and connector-metadata caches, but since a {@code CacheCategory.CONNECTIONS}
 * invalidation broadcast only clears the customer-scoped entry for a key (see {@link
 * DistributedCache}'s class doc), a user-scoped entry here doesn't get evicted the moment a
 * connection changes - it falls back to expiring on its own one-hour TTL.
 */
@Singleton
public class ConnectorToolDeclarationCache {

  private final ConnectorCacheService connectorCacheService;
  private final DistributedCache<FunctionDeclaration> cache;

  @Inject
  public ConnectorToolDeclarationCache(
      final ConnectorCacheService connectorCacheService,
      final DistributedCacheManager cacheManager) {
    this.connectorCacheService = connectorCacheService;
    this.cache =
        DistributedCache.<FunctionDeclaration>builder("TOOL_DECLARATION_CACHE", cacheManager)
            .scope(CacheScope.USER)
            .tags(Set.of(ConnectionCacheTag.CONNECTIONS))
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(this::build)
            .build();
  }

  public Optional<FunctionDeclaration> get(final String appName, final String connectorName) {
    return Optional.ofNullable(cache.get(appName + ":" + connectorName));
  }

  private FunctionDeclaration build(final String key) {
    final String[] parts = key.split(":", 2);
    if (parts.length != 2) {
      return null;
    }
    final String appName = parts[0];
    final String connectorName = parts[1];

    final ConnectorMetadata connectorMetadata =
        connectorCacheService.getConnectorMetadata(appName, connectorName);
    if (connectorMetadata == null) {
      return null;
    }
    final List<String> connectionIds = connectorCacheService.getConnectionsForApp(appName);

    final Map<String, Schema> properties = new HashMap<>();
    properties.put("input", Schema.fromJson(JsonUtils.toJson(connectorMetadata.inputSchema())));

    final List<String> requiredFields = new ArrayList<>();
    requiredFields.add("input");

    if (CollectionUtils.isNotEmpty(connectionIds)) {
      properties.put(
          "connectionId",
          Schema.builder()
              .type("STRING")
              .description("The connection ID to use.")
              .enum_(connectionIds)
              .build());
      requiredFields.add("connectionId");
    }

    final Schema schema =
        Schema.builder().type("OBJECT").properties(properties).required(requiredFields).build();

    return FunctionDeclaration.builder()
        .name(connectorName)
        .description(connectorMetadata.description())
        .parameters(schema)
        .build();
  }
}
