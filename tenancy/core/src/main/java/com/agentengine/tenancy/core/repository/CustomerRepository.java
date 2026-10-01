package com.agentengine.tenancy.core.repository;

import com.agentengine.tenancy.beans.Customer;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.repository.AbstractRepository;
import com.agentengine.util.common.repository.DocumentBackend;
import com.agentengine.util.common.repository.DocumentRepositorySpec;
import com.agentengine.util.common.validation.ValidationService;
import io.quarkus.runtime.Startup;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Startup
public class CustomerRepository extends AbstractRepository<Customer> {

  @Inject
  public CustomerRepository(
      final DocumentBackend documentBackend, final ValidationService validationService) {
    super(
        documentBackend.getEntityStore(
            DocumentRepositorySpec.global(TenancyDocumentStoreClientType.TENANCY, Customer.class)),
        validationService);
  }

  public Customer getByDomain(final String domain) {
    final PaginatedResult<Customer> result =
        findByQuery(
            new Query()
                .withFilter(Filters.eq(Customer.FIELD_DOMAIN, domain))
                .withPage(new Page(0, 1)));
    return result.getItems().stream().findFirst().orElse(null);
  }
}
