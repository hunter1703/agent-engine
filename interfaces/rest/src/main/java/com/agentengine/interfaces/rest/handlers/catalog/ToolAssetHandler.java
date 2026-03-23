package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.runtime.api.services.ToolCatalog;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class ToolAssetHandler extends NamedAssetHandler<ToolDescriptor> {

  private static final String ASSET_TYPE = "tool";

  private final ToolCatalog toolCatalog;

  @Inject
  public ToolAssetHandler(ToolCatalog toolCatalog) {
    this.toolCatalog = toolCatalog;
  }

  @Override
  public String getAssetType() {
    return ASSET_TYPE;
  }

  @Override
  public PaginatedResult<ToolDescriptor> findAssets(AssetRequest request) {
    List<ToolDescriptor> tools = toolCatalog.getTools();

    Page page = request.getQuery() != null && request.getQuery().getPage() != null ? request.getQuery().getPage() : new Page(0, 100);

    final long total = tools.size();
    final int start = (int) Math.min(page.getOffset(), total);
    final int end = (int) Math.min(start + page.getLimit(), total);
    final List<ToolDescriptor> pagedTools = tools.subList(start, end);

    return PaginatedResult.create(pagedTools, page, total);
  }

  @Override
  public Map<String, ToolDescriptor> getAssetsByIds(AssetRequest request) {
    if (request.getKeys() == null || request.getKeys().isEmpty()) {
      return Map.of();
    }
    return request.getKeys().stream().map(toolCatalog::getToolByName).filter(Objects::nonNull)
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
