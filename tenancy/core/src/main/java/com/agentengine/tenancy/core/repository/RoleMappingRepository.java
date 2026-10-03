package com.agentengine.tenancy.core.repository;

import com.agentengine.tenancy.core.rbac.RoleMapping;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.tasks.Task;
import com.agentengine.util.tasks.TaskStatus;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@Singleton
@Startup
public class RoleMappingRepository extends AbstractRepository<RoleMapping> {

  @Inject
  public RoleMappingRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(
                TenancyDocumentStoreClientType.TENANCY, RoleMapping.class)),
        validationService);
  }

  public List<RoleMapping> findForAsset(final String assetClass, final String assetId) {
    return findAll(assetFilter(assetClass, assetId));
  }

  public List<RoleMapping> findForRoles(final Collection<String> roleIds) {
    return findAll(Filters.in(RoleMapping.FIELD_ROLE_IDS, List.copyOf(roleIds)));
  }

  public RoleMapping addRoles(
      final String principal,
      final String assetClass,
      final String assetId,
      final Collection<String> roleIds,
      final TaskStatus status) {
    final List<Operation> operations =
        new ArrayList<>(
            List.of(
                Operation.addToSet(RoleMapping.FIELD_ROLE_IDS, roleIds),
                Operation.setOnInsert(RoleMapping.FIELD_PRINCIPAL, principal),
                Operation.setOnInsert(RoleMapping.FIELD_ASSET_CLASS, assetClass),
                Operation.setOnInsert(RoleMapping.FIELD_ASSET_ID, assetId)));
    if (assetId != null) {
      operations.add(Operation.set(Task.FIELD_STATUS, status.name()));
    }
    return upsertOne(
        Filters.eq(BaseEntity.FIELD_ID, RoleMapping.id(principal, assetClass, assetId)),
        new Update(operations));
  }

  /**
   * Removes the roles from the principal's mapping. A mapping on one asset becomes pending with it,
   * and stays, empty, until its asset's access list is recalculated; a mapping on every asset is
   * deleted once it holds no role.
   */
  public void removeRoles(
      final String principal,
      final String assetClass,
      final String assetId,
      final Collection<String> roleIds) {
    final String id = RoleMapping.id(principal, assetClass, assetId);
    if (assetId != null) {
      updateOneIgnoringVersion(
          Filters.eq(BaseEntity.FIELD_ID, id),
          Update.of(
              Operation.removeFromSet(RoleMapping.FIELD_ROLE_IDS, roleIds),
              Operation.set(Task.FIELD_STATUS, TaskStatus.PENDING.name())));
      return;
    }
    updateOneIgnoringVersion(
        Filters.eq(BaseEntity.FIELD_ID, id),
        Update.of(Operation.removeFromSet(RoleMapping.FIELD_ROLE_IDS, roleIds)));
    deleteByFilterIgnoringVersion(
        Filters.and(
            Filters.eq(BaseEntity.FIELD_ID, id),
            Filters.eq(RoleMapping.FIELD_ROLE_IDS, List.of())));
  }

  /**
   * Removes every role of {@code principal}, as {@link #removeRoles} does for each of its mappings:
   * those on one asset become empty and pending until their asset's access list is recalculated,
   * those on every asset are deleted.
   */
  public void removeAllRoles(final String principal) {
    final Filter ofPrincipal = Filters.eq(RoleMapping.FIELD_PRINCIPAL, principal);
    updateManyIgnoringVersion(
        Filters.and(ofPrincipal, Filters.ne(RoleMapping.FIELD_ASSET_ID, null)),
        Update.of(
            Operation.set(RoleMapping.FIELD_ROLE_IDS, List.of()),
            Operation.set(Task.FIELD_STATUS, TaskStatus.PENDING.name())));
    final Filter onEveryAsset =
        Filters.and(ofPrincipal, Filters.eq(RoleMapping.FIELD_ASSET_ID, null));
    updateManyIgnoringVersion(
        onEveryAsset, Update.of(Operation.set(RoleMapping.FIELD_ROLE_IDS, List.of())));
    deleteByFilterIgnoringVersion(onEveryAsset);
  }

  /**
   * Sets the mapping's status, only while it is at the version read.
   *
   * @throws StaleStateException if the mapping is gone or was written since
   */
  public void updateStatus(final RoleMapping mapping, final TaskStatus status) {
    update(mapping, Update.of(Operation.set(Task.FIELD_STATUS, status.name())));
  }

  /** Sets the status of every mapping on one asset that holds {@code roleId}, and returns them. */
  public List<RoleMapping> updateStatusOnAssetsWithRole(
      final String roleId, final TaskStatus status) {
    final Filter onAssetsWithRole =
        Filters.and(
            Filters.in(RoleMapping.FIELD_ROLE_IDS, List.of(roleId)),
            Filters.ne(RoleMapping.FIELD_ASSET_ID, null));
    updateManyIgnoringVersion(
        onAssetsWithRole, Update.of(Operation.set(Task.FIELD_STATUS, status.name())));
    return findAll(onAssetsWithRole);
  }

  /** The mappings with {@code status} last written before {@code updatedBefore}, epoch millis. */
  public List<RoleMapping> findWithStatus(final TaskStatus status, final long updatedBefore) {
    return findAll(
        Filters.and(
            Filters.eq(Task.FIELD_STATUS, status.name()),
            Filters.lt(BaseEntity.FIELD_UPDATED_TIME, updatedBefore)));
  }

  public void deleteForAssets(final String assetClass, final Collection<String> assetIds) {
    deleteByFilterIgnoringVersion(
        Filters.and(
            Filters.eq(RoleMapping.FIELD_ASSET_CLASS, assetClass),
            Filters.in(RoleMapping.FIELD_ASSET_ID, List.copyOf(assetIds))));
  }

  private List<RoleMapping> findAll(final Filter filter) {
    return findByQuery(new Query().withFilter(filter).withPage(Page.UNBOUNDED)).getItems();
  }

  private static Filter assetFilter(final String assetClass, final String assetId) {
    return Filters.and(
        Filters.eq(RoleMapping.FIELD_ASSET_CLASS, assetClass),
        Filters.eq(RoleMapping.FIELD_ASSET_ID, assetId));
  }
}
