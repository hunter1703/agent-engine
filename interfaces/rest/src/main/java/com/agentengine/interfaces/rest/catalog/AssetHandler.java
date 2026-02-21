package com.agentengine.interfaces.rest.catalog;

import com.agentengine.engine.api.beans.BaseEntity;
import com.agentengine.engine.api.utils.PaginatedResult;
import java.util.Map;

/** Interface for asset handlers that process requests for specific asset types */
public interface AssetHandler<T extends BaseEntity> {

  /** Returns the asset type this handler supports */
  String getAssetType();

  /** Lists assets based on the provided criteria */
  PaginatedResult<T> findAssets(AssetRequest request);

  /** Gets specific assets by their keys */
  Map<String, T> getAssetsByIds(AssetRequest request);
}
