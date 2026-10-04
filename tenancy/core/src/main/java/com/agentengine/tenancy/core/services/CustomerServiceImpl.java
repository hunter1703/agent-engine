package com.agentengine.tenancy.core.services;

import com.agentengine.tenancy.CustomerService;
import com.agentengine.tenancy.beans.Customer;
import com.agentengine.tenancy.core.repository.CustomerRepository;
import com.agentengine.util.common.exception.DuplicateAssetException;
import com.agentengine.util.distributed.DistributedCacheManager;
import io.quarkus.arc.Unremovable;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
@Unremovable
public class CustomerServiceImpl implements CustomerService {

  private final CustomerRepository customerRepository;
  private final DistributedCacheManager cacheManager;

  @Inject
  public CustomerServiceImpl(
      final CustomerRepository customerRepository, final DistributedCacheManager cacheManager) {
    this.customerRepository = customerRepository;
    this.cacheManager = cacheManager;
  }

  @Override
  public Customer getByDomain(final String domain) {
    return customerRepository.getByDomain(domain);
  }

  @Override
  public Customer create(final Customer customer) {
    if (customerRepository.getByDomain(customer.getDomain()) != null) {
      throw new DuplicateAssetException("Customer.domain", customer.getDomain());
    }
    final Customer created = customerRepository.insert(customer);
    // Its domain may be cached as unknown by the services resolving tenants.
    cacheManager.broadcastInvalidation(CUSTOMER_BY_DOMAIN_CACHE, created.getDomain());
    return created;
  }

  @Override
  public Customer update(final String id, final Customer customer) {
    Customer existing = customerRepository.findById(id);
    Customer updated = customerRepository.update(id, customer);
    cacheManager.broadcastInvalidation(CUSTOMER_BY_DOMAIN_CACHE, existing.getDomain());
    cacheManager.broadcastInvalidation(CUSTOMER_BY_DOMAIN_CACHE, updated.getDomain());
    return updated;
  }
}
