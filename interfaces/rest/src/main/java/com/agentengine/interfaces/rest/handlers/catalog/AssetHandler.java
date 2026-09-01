package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.query.PaginatedResult;
import java.util.Map;

public interface AssetHandler<T> {

  String getAssetType();

  PaginatedResult<T> findAssets(AssetRequest request);

  Map<String, T> getAssetsByIds(AssetRequest request);
}
