package com.agentengine.agent.infra.tools.connector;

import static com.agentengine.util.common.Constants.ID_SEPARATOR;

import com.agentengine.connectors.api.beans.ConnectorMetadata;
import com.agentengine.connectors.api.services.ConnectorCacheService;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.utils.CollectionUtils;
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
import java.util.concurrent.TimeUnit;

/**
 * Builds each connector's tool {@link FunctionDeclaration} — the "input"/"connectionId" wrapper
 * schema a {@link ConnectorTool} exposes to the model. A connector's description and parsed input
 * schema are cached for the whole customer; the connection ids the current context may use are
 * added on each call.
 */
@Singleton
public class ConnectorToolDeclarationCache {

  private final ConnectorCacheService connectorCacheService;
  private final DistributedCache<ConnectorSchema> cache;

  @Inject
  public ConnectorToolDeclarationCache(
      final ConnectorCacheService connectorCacheService,
      final DistributedCacheManager cacheManager) {
    this.connectorCacheService = connectorCacheService;
    this.cache =
        new DistributedCache.Builder<ConnectorSchema>("CONNECTOR_SCHEMA_CACHE", cacheManager)
            .localCache(CacheBuilder.newBuilder().expireAfterWrite(1, TimeUnit.HOURS))
            .loader(this::load)
            .build();
  }

  public Optional<FunctionDeclaration> get(final String appName, final String connectorName) {
    final ConnectorSchema connectorSchema = cache.get(appName + ID_SEPARATOR + connectorName);
    if (connectorSchema == null) {
      return Optional.empty();
    }
    final Map<String, Schema> properties = new HashMap<>();
    properties.put("input", connectorSchema.input());
    final List<String> requiredFields = new ArrayList<>();
    requiredFields.add("input");

    final List<String> connectionIds = connectorCacheService.getConnectionsForApp(appName);
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

    return Optional.of(
        FunctionDeclaration.builder()
            .name(connectorName)
            .description(connectorSchema.description())
            .parameters(
                Schema.builder()
                    .type("OBJECT")
                    .properties(properties)
                    .required(requiredFields)
                    .build())
            .build());
  }

  private ConnectorSchema load(final String key) {
    final String[] parts = key.split(ID_SEPARATOR, 2);
    if (parts.length != 2) {
      return null;
    }
    final ConnectorMetadata connectorMetadata =
        connectorCacheService.getConnectorMetadata(parts[0], parts[1]);
    return connectorMetadata == null
        ? null
        : new ConnectorSchema(
            connectorMetadata.description(),
            Schema.fromJson(JsonUtils.toJson(connectorMetadata.inputSchema())));
  }

  /** A connector's description and the schema of its input. */
  private record ConnectorSchema(String description, Schema input) {}
}
