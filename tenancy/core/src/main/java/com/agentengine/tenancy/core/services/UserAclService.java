package com.agentengine.tenancy.core.services;

import com.agentengine.tenancy.AssetAclService;
import com.agentengine.tenancy.beans.User;
import com.agentengine.tenancy.core.repository.TenancyDocumentStoreClientType;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.repository.EntityStore;
import com.agentengine.util.tenancy.AbstractAssetAclService;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

/**
 * Implements the {@link AssetAclService} for the {@link User} asset class directly against the
 * {@link EntityStore}, rather than routing through {@link
 * com.agentengine.tenancy.core.repository.UserRepository}.
 *
 * <p>This structural separation is necessary to avoid a boot-time dependency cycle: The RBAC engine
 * ({@link com.agentengine.tenancy.core.rbac.AclCalculator}) requires an {@link AssetAclService} to
 * read/update ACLs. If this were implemented by the main {@link
 * com.agentengine.tenancy.UserService} or repository, it would cause a cycle because repositories
 * require {@link com.agentengine.tenancy.AccessControlService} to enforce permissions on normal
 * operations. By querying the store directly here, we decouple ACL management from permission
 * enforcement.
 *
 * <p>The issue exists only for permissioned entities in tenancy:core, for rest of the entities,
 * microservice boundary saves us
 */
@Singleton
public class UserAclService extends AbstractAssetAclService<User> implements AssetAclService {

  @Inject
  public UserAclService(final DocumentBackend documentBackend) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                TenancyDocumentStoreClientType.TENANCY, User.class)));
  }
}
