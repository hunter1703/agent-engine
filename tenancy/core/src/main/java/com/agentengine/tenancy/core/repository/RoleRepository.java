package com.agentengine.tenancy.core.repository;

import com.agentengine.tenancy.core.rbac.Role;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.exception.StaleStateException;
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
import java.util.List;

@Singleton
@Startup
public class RoleRepository extends AbstractRepository<Role> {

  @Inject
  public RoleRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.perCustomer(TenancyDocumentStoreClientType.TENANCY, Role.class)),
        validationService);
  }

  /**
   * Sets the role's status, only while it is at the version read.
   *
   * @throws StaleStateException if the role is gone or was written since
   */
  public void updateStatus(final Role role, final TaskStatus status) {
    update(role, Update.of(Operation.set(Task.FIELD_STATUS, status.name())));
  }

  /** The roles with {@code status} last written before {@code updatedBefore}, epoch millis. */
  public List<Role> findWithStatus(final TaskStatus status, final long updatedBefore) {
    return findByQuery(
            new Query()
                .withFilter(
                    Filters.and(
                        Filters.eq(Task.FIELD_STATUS, status.name()),
                        Filters.lt(BaseEntity.FIELD_UPDATED_TIME, updatedBefore)))
                .withPage(Page.UNBOUNDED))
        .getItems();
  }
}
