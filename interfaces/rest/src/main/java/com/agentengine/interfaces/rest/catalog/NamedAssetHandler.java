package com.agentengine.interfaces.rest.catalog;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.beans.NamedEntity;
import com.agentengine.engine.utils.Page;
import com.agentengine.engine.utils.PaginatedResult;

public abstract class NamedAssetHandler<T extends NamedEntity> implements AssetHandler<T> {

    public PaginatedResult<NameIdEntity> listAssets(final AssetRequest request) {
        final PaginatedResult<T> assets = findAssets(request);
        final Page page = request.getQuery() == null ? new Page() : request.getQuery().getPage();
        return PaginatedResult.create(assets.getItems().stream()
                .map(asset -> new NameIdEntity(asset.getId(), asset.getName()))
                .toList(), page);
    }
}
