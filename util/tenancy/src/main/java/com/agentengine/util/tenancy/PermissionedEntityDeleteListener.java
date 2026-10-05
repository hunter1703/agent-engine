package com.agentengine.util.tenancy;

import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.GlobalEntityChangeListener;
import com.agentengine.util.context.Context;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;

/**
 * Listens for deletions of any entity and cleans up their access lists if they are permissioned.
 */
@Singleton
@Unremovable
public class PermissionedEntityDeleteListener implements GlobalEntityChangeListener {

  private final AclService aclService;

  @Inject
  public PermissionedEntityDeleteListener(final AclService aclService) {
    this.aclService = aclService;
  }

  @Override
  public Class<BaseEntity> entityClass() {
    throw new UnsupportedOperationException(
        "Global listener does not have a specific entity class");
  }

  @Override
  public void onChange(EntityChange<BaseEntity> change) {}

  @Override
  public void onChange(Class<BaseEntity> entityClass, EntityChange<BaseEntity> change) {
    if (change.type() != EntityChange.Type.DELETED || entityClass == null) {
      return;
    }

    final String assetClass = entityClass.getSimpleName();
    // Only permissioned entities have their ACLs managed by AclService
    if (!entityClass.isAnnotationPresent(Permissioned.class)) {
      return;
    }

    final List<String> ids =
        switch (change) {
          case EntityChange.Ids<?> idsChange -> List.copyOf(idsChange.ids());
          case EntityChange.Entities<?> entitiesChange ->
              List.copyOf(entitiesChange.idVsEntity().keySet());
        };

    if (ids.isEmpty()) {
      return;
    }

    Context.require().asSystemCaller().run(() -> aclService.deleteAcls(assetClass, ids));
  }
}
