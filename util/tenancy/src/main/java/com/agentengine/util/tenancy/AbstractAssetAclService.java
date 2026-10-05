package com.agentengine.util.tenancy;

import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.repository.EntityStore;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractAssetAclService<T extends BaseEntity> {
  protected final EntityStore<T> store;

  protected AbstractAssetAclService(final EntityStore<T> store) {
    this.store = store;
  }

  public Map<String, Acl> getAcls(String assetClass, Collection<String> assetIds) {
    final Map<String, Acl> idVsAcl = new LinkedHashMap<>();
    store
        .findByIds(assetIds, List.of(BaseEntity.FIELD_ACL), null)
        .forEach((id, entity) -> idVsAcl.put(id, entity.getAcl()));
    return idVsAcl;
  }

  public Set<String> applyAcls(String assetClass, Map<String, Acl> assetIdVsAcl) {
    final Set<String> applied = new LinkedHashSet<>();
    assetIdVsAcl.forEach(
        (id, acl) -> {
          final Filter older =
              Filters.and(
                  Filters.eq(BaseEntity.FIELD_ID, id),
                  Filters.lt(BaseEntity.FIELD_ACL_VERSION, acl.version()));
          final Update apply =
              Update.of(
                  Operation.set(BaseEntity.FIELD_ACL, JsonUtils.toMap(acl)),
                  Operation.inc(BaseEntity.FIELD_VERSION, 1L));
          if (store.updateOne(older, apply) > 0) {
            applied.add(id);
          }
        });
    return applied;
  }
}
