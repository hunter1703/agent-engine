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

@Singleton
public class ConnectorAssetHandler extends NamedAssetHandler<NamedEntity> {

  public static final String OPTION_APP_NAME = "appName";

  private final ConnectorService connectorService;

  @Inject
  public ConnectorAssetHandler(final ConnectorService connectorService) {
    this.connectorService = connectorService;
  }

  @Override
  public String getAssetType() {
    return AssetClass.CONNECTOR;
  }

  @Override
  public PaginatedResult<NamedEntity> findAssets(final AssetRequest request) {
    final String appName = appName(request);
    final List<NamedEntity> entities =
        connectorService.findAllConnectorNames(appName).stream()
            .map(name -> new NamedEntity(name, name))
            .toList();
    return PaginatedResult.create(entities);
  }

  @Override
  public Map<String, NamedEntity> getAssetsByIds(final AssetRequest request) {
    if (CollectionUtils.isEmpty(request.getKeys())) {
      return Map.of();
    }
    final String appName = appName(request);
    final Set<String> connectors = new HashSet<>(connectorService.findAllConnectorNames(appName));
    return request.getKeys().stream()
        .filter(connectors::contains)
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

  private static String appName(final AssetRequest request) {
    return request != null
        ? CollectionUtils.getStringValueFromMap(request.getOptions(), OPTION_APP_NAME)
        : null;
  }
}
