package com.agentengine.interfaces.rest.catalog.handlers;

import com.agentengine.engine.api.services.ToolService;
import com.agentengine.engine.api.tools.ToolDescriptor;
import com.agentengine.engine.api.query.Page;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.interfaces.rest.catalog.AssetRequest;
import com.agentengine.interfaces.rest.catalog.NamedAssetHandler;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ToolAssetHandler extends NamedAssetHandler<ToolDescriptor> {

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
  public PaginatedResult<ToolDescriptor> findAssets(AssetRequest request) {
    List<ToolDescriptor> tools = toolService.getVisibleTools(null);

    Page page =
        request.getQuery() != null && request.getQuery().getPage() != null
            ? request.getQuery().getPage()
            : new Page(0, 100);

    return PaginatedResult.create(tools, page, tools.size());
  }

  @Override
  public Map<String, ToolDescriptor> getAssetsByIds(AssetRequest request) {
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return Map.of();
    }
    return findAssets(request).getItems().stream()
        .filter(t -> request.getKeys().contains(t.name()))
        .collect(Collectors.toMap(ToolDescriptor::name, Function.identity()));
  }

  @Override
  protected String getId(ToolDescriptor asset) {
    return asset.name();
  }

  @Override
  protected String getName(ToolDescriptor asset) {
    return asset.name();
  }
}
