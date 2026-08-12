package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.agent.api.services.ToolCatalog;
import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.agents.beans.tools.ToolDescriptor;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

@Singleton
public class StandardToolAssetHandler extends NamedAssetHandler<ToolDescriptor> {

    private final ToolCatalog toolCatalog;

    @Inject
    public StandardToolAssetHandler(ToolCatalog toolCatalog) {
        this.toolCatalog = toolCatalog;
    }

    @Override
    public String getAssetType() {
        return AssetClass.STANDARD_TOOL;
    }

    @Override
    public PaginatedResult<ToolDescriptor> findAssets(AssetRequest request) {
        final Query query = request.getQuery();
        final Page page = query == null ? null : query.getPage();
        final List<ToolDescriptor> standardTools = toolCatalog.getStandardTools();
        return PaginatedResult.create(standardTools, page, (long) standardTools.size());
    }

    @Override
    public Map<String, ToolDescriptor> getAssetsByIds(AssetRequest request) {
        final Map<String, ToolDescriptor> standardToolsMap = CollectionUtils.transformToMap(
                toolCatalog.getStandardTools(), ToolDescriptor::name, Function.identity());
        return request.getKeys().stream()
                .map(standardToolsMap::get)
                .filter(Objects::nonNull)
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
