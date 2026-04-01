package com.agentengine.interfaces.rest.handlers.catalog;

import com.agentengine.interfaces.rest.dto.AssetRequest;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.PaginatedResult;

public abstract class NamedAssetHandler<T> implements AssetHandler<T> {

    public PaginatedResult<? extends NamedEntity> listAssets(final AssetRequest request) {
        final PaginatedResult<T> assets = findAssets(request);
        return assets.transform(this::toNamedEntity);
    }

    protected NamedEntity toNamedEntity(final T asset) {
        return new NamedEntity(getId(asset), getName(asset));
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
