package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.connectors.api.services.ConnectorService;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.PaginatedResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ConnectorAssetHandler extends NamedAssetHandler<String> {

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
  public PaginatedResult<String> findAssets(final AssetRequest request) {
    final String appName = appName(request);
    if (StringUtils.isBlank(appName)) {
      return PaginatedResult.create(List.of());
    }
    return PaginatedResult.create(connectorService.findAllConnectorNames(appName));
  }

  @Override
  public Map<String, String> getAssetsByIds(final AssetRequest request) {
    final String appName = appName(request);
    if (StringUtils.isBlank(appName) || CollectionUtils.isEmpty(request.getKeys())) {
      return Map.of();
    }
    final Set<String> connectors = new HashSet<>(connectorService.findAllConnectorNames(appName));
    return request.getKeys().stream()
        .filter(connectors::contains)
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

  private static String appName(final AssetRequest request) {
    return CollectionUtils.getStringValueFromMap(request.getOptions(), OPTION_APP_NAME);
  }
}
