package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.PaginatedResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resource catalog lookup for the names of connector apps currently bundled in the connectors
 * service.
 */
@Singleton
public class ConnectorAppAssetHandler extends NamedAssetHandler<String> {

  private final ConnectorService connectorService;

  @Inject
  public ConnectorAppAssetHandler(final ConnectorService connectorService) {
    this.connectorService = connectorService;
  }

  @Override
  public String getAssetType() {
    return AssetClass.CONNECTOR_APP;
  }

  @Override
  public PaginatedResult<String> findAssets(final AssetRequest request) {
    return PaginatedResult.create(connectorService.findAllConnectorAppNames());
  }

  @Override
  public Map<String, String> getAssetsByIds(final AssetRequest request) {
    if (CollectionUtils.isEmpty(request.getKeys())) {
      return Map.of();
    }
    final Set<String> apps = new HashSet<>(connectorService.findAllConnectorAppNames());
    return request.getKeys().stream()
        .filter(apps::contains)
        .collect(Collectors.toMap(Function.identity(), Function.identity()));
  }

  @Override
  protected String getId(final String asset) {
    return asset;
  }

  @Override
  protected String getName(final String asset) {
    return asset;
  }
}
