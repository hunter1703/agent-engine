package com.agentengine.tenancy.core.repository;

import com.agentengine.tenancy.AccessControlService;
import com.agentengine.tenancy.beans.User;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.UnauthorizedException;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.utils.CollectionUtils;
import com.agentengine.util.common.utils.StringUtils;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.SequenceRepository;
import com.agentengine.util.tenancy.AbstractPermissionedRepository;
import com.agentengine.util.tenancy.PermissionChecker;
import io.quarkus.elytron.security.common.BcryptUtil;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Singleton
@Startup
public class UserRepository extends AbstractPermissionedRepository<User> {

  private static final String UNMATCHABLE_HASH =
      BcryptUtil.bcryptHash(UUID.randomUUID().toString());

  private final SequenceRepository sequenceRepository;

  @Inject
  public UserRepository(
      final DocumentBackend documentBackend,
      final ValidationService validationService,
      final PermissionChecker permissionChecker,
      final AccessControlService accessControlService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(TenancyDocumentStoreClientType.TENANCY, User.class)),
        validationService,
        permissionChecker,
        accessControlService);
    this.sequenceRepository = new SequenceRepository(documentBackend, validationService);
  }

  public User create(final User user, final String password) {
    user.setId(String.valueOf(sequenceRepository.increment(AssetClass.USER)));
    user.setPasswordHash(BcryptUtil.bcryptHash(password));
    final User created = insert(user);
    created.setPasswordHash(null);
    return created;
  }

  public User authenticate(final String username, final String password) {
    final User user =
        super.findByQuery(getUserNameQuery(username)).getItems().stream().findFirst().orElse(null);
    final String hash =
        user == null || StringUtils.isBlank(user.getPasswordHash())
            ? UNMATCHABLE_HASH
            : user.getPasswordHash();
    final boolean matches = BcryptUtil.matches(Objects.requireNonNullElse(password, ""), hash);
    if (user == null) {
      return null;
    }
    user.setPasswordHash(null);
    return matches ? user : null;
  }

  @Override
  public User findById(
      final String id, final List<String> includeFields, final List<String> excludeFields) {
    return super.findById(id, includeFields, withPasswordHash(excludeFields));
  }

  @Override
  public Map<String, User> findByIds(
      final Collection<String> ids,
      final List<String> includeFields,
      final List<String> excludeFields) {
    return super.findByIds(ids, includeFields, withPasswordHash(excludeFields));
  }

  @Override
  public PaginatedResult<User> findByQuery(final Query query) {
    return super.findByQuery(withoutPasswordHash(query));
  }

  @Override
  public User updateIgnoringVersion(final String id, final Update update) {
    final User updated =
        findOneAndUpdateIgnoringVersion(
            new Query().withFilter(Filters.eq(BaseEntity.FIELD_ID, id)), update);
    if (updated == null) {
      throw new UnauthorizedException(User.class.getSimpleName(), id);
    }
    return updated;
  }

  @Override
  public User findOneAndUpdateIgnoringVersion(final Query query, final Update update) {
    return super.findOneAndUpdateIgnoringVersion(withoutPasswordHash(query), update);
  }

  private static Query getUserNameQuery(final String username) {
    return new Query()
        .withFilter(Filters.eq(User.FIELD_USERNAME, username))
        .withPage(new Page(0, 1));
  }

  private static Query withoutPasswordHash(final Query query) {
    final Query source = query == null ? new Query() : query;
    return new Query(source).withExcludeFields(withPasswordHash(source.getExcludeFields()));
  }

  private static List<String> withPasswordHash(final List<String> excludeFields) {
    final List<String> fields = new ArrayList<>(CollectionUtils.nullSafeList(excludeFields));
    if (!fields.contains(User.FIELD_PASSWORD_HASH)) {
      fields.add(User.FIELD_PASSWORD_HASH);
    }
    return fields;
  }
}
