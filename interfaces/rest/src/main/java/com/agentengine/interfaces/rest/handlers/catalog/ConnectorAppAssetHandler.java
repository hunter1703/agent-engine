package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.utils.CollectionUtils;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Resource catalog lookup for the names of connector apps currently bundled in the connectors
 * service.
 */
@Singleton
public class ConnectorAppAssetHandler extends NamedAssetHandler<NamedEntity> {

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
  public PaginatedResult<NamedEntity> findAssets(final AssetRequest request) {
    final List<NamedEntity> entities =
        connectorService.findAllConnectorAppNames().stream()
            .map(name -> new NamedEntity(name, name))
            .toList();
    return PaginatedResult.create(entities);
  }

  @Override
  public Map<String, NamedEntity> getAssetsByIds(final AssetRequest request) {
    if (CollectionUtils.isEmpty(request.getKeys())) {
      return Map.of();
    }
    final Set<String> apps = new HashSet<>(connectorService.findAllConnectorAppNames());
    return request.getKeys().stream()
        .filter(apps::contains)
        .collect(Collectors.toMap(Function.identity(), name -> new NamedEntity(name, name)));
  }

  @Override
  protected String getId(final NamedEntity asset) {
    return asset != null ? asset.getId() : null;
  }

  @Override
  protected String getName(final NamedEntity asset) {
    return asset != null ? asset.getName() : null;
  }
}
