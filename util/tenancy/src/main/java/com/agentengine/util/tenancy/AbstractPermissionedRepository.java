package com.agentengine.util.tenancy;

import com.agentengine.util.common.annotations.Permissioned;
import com.agentengine.util.common.beans.Acl;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.codec.JsonUtils;
import com.agentengine.util.common.exception.ConfigurationException;
import com.agentengine.util.common.exception.StaleStateException;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.EntityChange;
import com.agentengine.util.common.repository.EntityStore;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.context.Context;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link PermissionedRepository} that enforces access on every read and write. Access to an
 * entity is the grants on it plus the context principals' roles on every asset of its class. A new
 * entity is shared as {@link #getInitialShare} says before it is stored, and stored with the access
 * list that sharing makes; the tenancy service keeps its access list from then on.
 */
public abstract class AbstractPermissionedRepository<T extends BaseEntity>
    extends AbstractRepository<T> implements PermissionedRepository<T> {

  private static final Logger LOG = LoggerFactory.getLogger(AbstractPermissionedRepository.class);

  /** The fields a permission check on a loaded entity reads. */
  private static final List<String> PERMISSION_FIELDS = List.of(BaseEntity.FIELD_ACL);

  private static final int DELETE_PAGE_SIZE = 500;

  private final String assetClass;
  private final PermissionChecker permissionChecker;
  private final AclService aclService;

  protected AbstractPermissionedRepository(
      final EntityStore<T> store,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AclService aclService) {
    super(store, validationService);
    final Permissioned permissioned = entityClass.getAnnotation(Permissioned.class);
    if (permissioned == null) {
      throw new ConfigurationException(entityClass.getSimpleName() + " is not @Permissioned");
    }
    this.assetClass = permissioned.assetClass();
    this.permissionChecker = permissionChecker;
    this.aclService = aclService;
  }

  @Override
  public T findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    final T entity =
        super.findById(
            id, withPermissionFields(includeFields), withoutPermissionFields(excludeFields));
    return entity != null && hasPermission(entity, Permission.READ) ? entity : null;
  }

  @Override
  public Map<String, T> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    final Map<String, T> found =
        super.findByIds(
            ids, withPermissionFields(includeFields), withoutPermissionFields(excludeFields));
    final Map<String, T> permitted = new LinkedHashMap<>(found);
    permitted.values().removeIf(entity -> !hasPermission(entity, Permission.READ));
    return permitted;
  }

  @Override
  public PaginatedResult<T> findByQuery(final Query query) {
    return super.findByQuery(decorateWithPermissionFilter(query, Permission.READ));
  }

  /**
   * @throws UnauthorizedException when the caller may not edit the entity
   */
  @Override
  public T update(final T entity, final Update update) {
    final T updated =
        updateFirst(
            new Query()
                .withFilter(
                    decorateWithPermissionFilter(withIdAndVersionFilter(entity), Permission.EDIT)),
            update);
    if (updated != null) {
      return updated;
    }
    if (!hasPermission(entity.getId(), Permission.EDIT)) {
      throw new UnauthorizedException(entityClass.getSimpleName(), entity.getId());
    }
    throw new StaleStateException(entity.getId(), entity.getVersion());
  }

  /**
   * @throws UnauthorizedException when the caller may not edit the entity, or it does not exist
   */
  @Override
  public T updateIgnoringVersion(final String id, final Update update) {
    final T updated =
        updateFirst(
            new Query()
                .withFilter(
                    decorateWithPermissionFilter(
                        Filters.eq(BaseEntity.FIELD_ID, id), Permission.EDIT)),
            update);
    if (updated == null) {
      throw new UnauthorizedException(entityClass.getSimpleName(), id);
    }
    return updated;
  }

  @Override
  public T findOneAndUpdateIgnoringVersion(final Query query, final Update update) {
    return updateFirst(decorateWithPermissionFilter(query, Permission.EDIT), update);
  }

  @Override
  public long updateOneIgnoringVersion(final Filter filter, final Update update) {
    return updateMatching(decorateWithPermissionFilter(filter, Permission.EDIT), update, true);
  }

  @Override
  public long updateManyIgnoringVersion(final Filter filter, final Update update) {
    return updateMatching(decorateWithPermissionFilter(filter, Permission.EDIT), update, false);
  }

  @Override
  public boolean delete(final T entity) {
    if (!hasPermission(entity.getId(), Permission.DELETE)) {
      return false;
    }
    final boolean deleted = deleteFromStore(entity);
    if (deleted) {
      forgetAcls(List.of(entity.getId()));
    }
    return deleted;
  }

  @Override
  public boolean deleteByIdIgnoringVersion(final String id) {
    if (!hasPermission(id, Permission.DELETE)) {
      return false;
    }
    final boolean deleted = deleteFromStore(id);
    if (deleted) {
      forgetAcls(List.of(id));
    }
    return deleted;
  }

  @Override
  public void deleteByFilterIgnoringVersion(final Filter filter) {
    final Filter deletable = decorateWithPermissionFilter(filter, Permission.DELETE);
    deleteMatching(deletable);
    publish(new EntityChange.Matching<>(EntityChange.Type.DELETED, deletable));
  }

  @Override
  public final Set<String> findPermittedIds(
      final Collection<String> ids, final Permission permission) {
    if (CollectionUtils.isEmpty(ids)) {
      return new LinkedHashSet<>();
    }
    return getPermittedIds(super.findByIds(ids, PERMISSION_FIELDS, null), permission);
  }

  @Override
  public final PaginatedResult<String> findPermittedIds(
      final Query query, final Permission permission) {
    return store
        .findByQuery(
            new Query(query)
                .withFilter(decorateWithPermissionFilter(query.getFilter(), permission))
                .withIncludeFields(List.of(BaseEntity.FIELD_ID)))
        .transform(BaseEntity::getId);
  }

  @Override
  public final boolean hasPermission(final String id, final Permission permission) {
    return permissionChecker.hasPermission(
        () -> super.findById(id, PERMISSION_FIELDS, null), assetClass, permission);
  }

  /** Whether the caller holds {@code permission} on an already loaded entity with its grants. */
  public final boolean hasPermission(final T entity, final Permission permission) {
    return permissionChecker.hasPermission(() -> entity, assetClass, permission);
  }

  public final void requirePermission(final String id, final Permission permission) {
    if (!hasPermission(id, permission)) {
      throw new UnauthorizedException(entityClass.getSimpleName(), id);
    }
  }

  @Override
  public final Map<String, Acl> readAcls(final Collection<String> ids) {
    requireSystem();
    final Map<String, Acl> idVsAcl = new LinkedHashMap<>();
    super.findByIds(ids, PERMISSION_FIELDS, null)
        .forEach((id, entity) -> idVsAcl.put(id, entity.getAcl()));
    return idVsAcl;
  }

  @Override
  public final Set<String> applyAcls(final Map<String, Acl> idVsAcl) {
    requireSystem();
    final Set<String> applied = new LinkedHashSet<>();
    idVsAcl.forEach(
        (id, acl) -> {
          final Filter older =
              Filters.and(
                  Filters.eq(BaseEntity.FIELD_ID, id),
                  Filters.lt(BaseEntity.FIELD_ACL_VERSION, acl.version()));
          final Update apply = Update.of(Operation.set(BaseEntity.FIELD_ACL, JsonUtils.toMap(acl)));
          if (store.updateOne(older, apply) > 0) {
            applied.add(id);
          }
        });
    if (!applied.isEmpty()) {
      publish(new EntityChange.Ids<>(EntityChange.Type.ACCESS_CHANGED, applied));
    }
    return applied;
  }

  /**
   * How a newly created entity is shared, each change adding roles on it alone: {@code manager} for
   * the principal creating it — what a user creates directly is theirs, what is created within a
   * context belongs to that context. Nothing when the system creates it. A class may share it with
   * another principal instead, or with more.
   */
  protected List<SharingChange> getInitialShare(final T entity) {
    return Context.currentPrincipal()
        .map(creator -> List.of(buildShare(entity, creator.toString(), StandardRole.MANAGER)))
        .orElse(List.of());
  }

  /** The change giving {@code principal} {@code role} on the new {@code entity}. */
  protected final SharingChange buildShare(
      final T entity, final String principal, final String role) {
    return SharingChange.ofAsset(assetClass, entity.getId(), Map.of(principal, Set.of(role)));
  }

  /**
   * Throws unless the caller may create {@code entities}: CREATE on every asset of the class. Runs
   * after they are validated and before they are stored.
   */
  protected void requireCreatePermission(final List<T> entities) {
    if (!permissionChecker.hasPermissionOnEveryAsset(assetClass, Permission.CREATE)) {
      throw new UnauthorizedException(entityClass.getSimpleName(), null);
    }
  }

  /**
   * Checks the caller may create the entities, shares them first, and stores each with the access
   * list its sharing makes, so whoever it is shared with reaches it as soon as it is stored. The
   * role mappings are forgotten again when storing fails.
   */
  @Override
  protected final List<T> create(final List<T> entities) {
    requireCreatePermission(entities);
    final List<T> prepared = prepareNewEntities(entities);
    final Map<String, Acl> idVsAcl = shareNewEntities(prepared);
    for (final T entity : prepared) {
      entity.setAcl(idVsAcl.getOrDefault(entity.getId(), Acl.EMPTY));
    }
    try {
      return storeNew(prepared);
    } catch (final RuntimeException exception) {
      forgetAcls(List.copyOf(idVsAcl.keySet()));
      throw exception;
    }
  }

  /**
   * Checks the caller may edit the stored entity, or creates it as a new one when there is none.
   * Like any replace, the write goes through only while the entity is at the version read; the
   * stored access list is kept whatever the replacement carries, so one applied meanwhile is never
   * overwritten.
   */
  @Override
  protected final T replace(
      final String id, final Long expectedVersion, final T entity, final boolean upsert) {
    validateEntity(entity);
    final T existing = readStored(id);
    if (existing == null && upsert) {
      entity.setId(id);
      return create(List.of(entity)).getFirst();
    }
    requireEditPermission(existing);
    return write(id, expectedVersion, entity, upsert, existing);
  }

  protected final void requireEditPermission(final T entity) {
    if (entity != null && !hasPermission(entity, Permission.EDIT)) {
      throw new UnauthorizedException(entityClass.getSimpleName(), entity.getId());
    }
  }

  protected final Query decorateWithPermissionFilter(
      final Query query, final Permission permission) {
    final Query source = query == null ? new Query() : query;
    return new Query(source)
        .withFilter(decorateWithPermissionFilter(source.getFilter(), permission));
  }

  /** Narrows {@code filter} to the entities the caller holds {@code permission} on. */
  protected final Filter decorateWithPermissionFilter(
      final Filter filter, final Permission permission) {
    if (permissionChecker.hasPermissionOnEveryAsset(assetClass, permission)) {
      return filter;
    }
    final Filter access = PermissionUtils.contextGrantsFilter(permission);
    return filter == null ? access : Filters.and(filter, access);
  }

  /** The ids in {@code found} the caller holds {@code permission} on. */
  private Set<String> getPermittedIds(final Map<String, T> found, final Permission permission) {
    final Set<String> permitted = new LinkedHashSet<>();
    found.forEach(
        (id, entity) -> {
          if (hasPermission(entity, permission)) {
            permitted.add(id);
          }
        });
    return permitted;
  }

  /** A permission check reads the entity's permission fields, so a projection must keep them. */
  private List<String> withPermissionFields(final List<String> includeFields) {
    if (CollectionUtils.isEmpty(includeFields)) {
      return includeFields;
    }
    final Set<String> fields = new LinkedHashSet<>(includeFields);
    fields.addAll(PERMISSION_FIELDS);
    return new ArrayList<>(fields);
  }

  private List<String> withoutPermissionFields(final List<String> excludeFields) {
    if (CollectionUtils.isEmpty(excludeFields)) {
      return excludeFields;
    }
    final List<String> fields = new ArrayList<>(excludeFields);
    fields.removeAll(PERMISSION_FIELDS);
    return fields;
  }

  /**
   * Deletes the entities {@code filter} matches, then forgets their access lists. Ids are read a
   * page at a time, each page deleted before the next is read.
   */
  private void deleteMatching(final Filter filter) {
    if (!inCustomer()) {
      store.deleteByFilter(filter);
      return;
    }
    final Query firstPage =
        new Query()
            .withFilter(filter)
            .withIncludeFields(List.of(BaseEntity.FIELD_ID))
            .withPage(new Page(0, DELETE_PAGE_SIZE));
    List<String> ids;
    do {
      ids = super.findByQuery(firstPage).getItems().stream().map(BaseEntity::getId).toList();
      if (!ids.isEmpty()) {
        store.deleteByFilter(Filters.in(BaseEntity.FIELD_ID, ids));
        forgetAcls(ids);
      }
    } while (ids.size() == DELETE_PAGE_SIZE);
  }

  /**
   * Tells the tenancy service to forget the access lists of deleted entities, if it can, as the
   * system: the caller's right to delete them is already checked.
   */
  private void forgetAcls(final List<String> ids) {
    if (!inCustomer() || ids.isEmpty()) {
      return;
    }
    try {
      Context.require().asSystemCaller().run(() -> aclService.deleteAcls(assetClass, ids));
    } catch (final RuntimeException exception) {
      LOG.warn(
          "Could not forget the access lists of {} {}",
          entityClass.getSimpleName(),
          ids,
          exception);
    }
  }

  /**
   * Shares the new entities as {@link #getInitialShare} says, as the system — the caller's right to
   * create them is already checked — and returns the access list each is to be stored with, by id.
   *
   * @throws IllegalStateException when a change shares anything but the new entity it is for: its
   *     role mappings would be taken as already applied, and never reach that other asset
   */
  private Map<String, Acl> shareNewEntities(final List<T> entities) {
    if (!inCustomer()) {
      return Map.of();
    }
    final List<SharingChange> changes = new ArrayList<>();
    for (final T entity : entities) {
      for (final SharingChange change : getInitialShare(entity)) {
        if (!assetClass.equals(change.assetClass()) || !entity.getId().equals(change.assetId())) {
          throw new IllegalStateException(
              "The initial share of %s %s shares another asset"
                  .formatted(assetClass, entity.getId()));
        }
        changes.add(change);
      }
    }
    if (changes.isEmpty()) {
      return Map.of();
    }
    return Context.require().asSystemCaller().get(() -> aclService.shareNewAssets(changes));
  }

  private static void requireSystem() {
    if (!Context.current().map(Context::isSystem).orElse(false)) {
      throw new UnauthorizedException("Access lists are kept by the system only");
    }
  }

  /**
   * Whether the context is in a customer, where tenancy keeps access lists: access lists belong to
   * a customer, so there are none outside of one.
   */
  private static boolean inCustomer() {
    return Context.currentCustomerId()
        .filter(id -> !Context.SYSTEM_CUSTOMER_ID.equals(id))
        .isPresent();
  }
}
