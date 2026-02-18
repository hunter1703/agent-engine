package com.agentengine.interfaces.rest.catalog;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.utils.Page;
import com.agentengine.engine.api.utils.PaginatedResult;

public abstract class NamedAssetHandler<T extends BaseEntity> implements AssetHandler<T> {

  public PaginatedResult<NameIdEntity> listAssets(final AssetRequest request) {
    final PaginatedResult<T> assets = findAssets(request);
    final Page page = request.getQuery() == null ? new Page() : request.getQuery().getPage();
    return PaginatedResult.create(
        assets.getItems().stream().map(asset -> new NameIdEntity(asset.getId(), getName(asset))).toList(), page);
  }

  protected abstract String getName(T asset);
}
