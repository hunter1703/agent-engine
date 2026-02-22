package com.agentengine.interfaces.rest.catalog;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.beans.NamedEntity;
import com.agentengine.engine.api.query.Page;
import com.agentengine.engine.api.query.PaginatedResult;

public abstract class NamedAssetHandler<T> implements AssetHandler<T> {

  public PaginatedResult<NameIdEntity> listAssets(final AssetRequest request) {
    final PaginatedResult<T> assets = findAssets(request);
    final Page page = request.getQuery() == null ? new Page() : request.getQuery().getPage();
    return PaginatedResult.create(
        assets.getItems().stream()
            .map(asset -> new NameIdEntity(getId(asset), getName(asset)))
            .toList(),
        page);
  }

  protected String getId(T asset) {
    if (asset instanceof BaseEntity baseEntity) {
      return baseEntity.getId();
    }
    return null;
  }

  protected String getName(T asset) {
    if (asset instanceof NamedEntity namedEntity) {
      return namedEntity.getName();
    }
    return null;
  }
}
