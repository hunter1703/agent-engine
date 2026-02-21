package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.beans.ToolEntity;
import com.agentengine.engine.api.services.ToolService;
import com.agentengine.engine.api.utils.Page;
import com.agentengine.engine.api.utils.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Singleton
public class ToolAssetHandler extends NamedAssetHandler<ToolEntity> {

  private static final String ASSET_TYPE = "tool";

  private final ToolService toolService;

  @Inject
  public ToolAssetHandler(ToolService toolService) {
    this.toolService = toolService;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<ToolEntity> findAssets(AssetRequest request) {
    List<ToolEntity> tools = toolService.getAvailableTools(null);

    Page page = request.getQuery() != null && request.getQuery().getPage() != null
        ? request.getQuery().getPage()
        : new Page(0, 100);

    return PaginatedResult.create(tools, page, tools.size());
  }

  @Override
  public Map<String, ToolEntity> getAssetsByIds(AssetRequest request) {
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return Map.of();
    }
    return findAssets(request).getItems().stream().filter(t -> request.getKeys().contains(t.getId()))
        .collect(Collectors.toMap(ToolEntity::getId, t -> t));
  }

  @Override
  protected String getName(ToolEntity asset) {
    return asset.getName();
  }
}
