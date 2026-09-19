package com.agentengine.tenancy;

import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.GlobalMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class CustomerRepository extends GlobalMongoRepository<Customer> {

  @Inject
  public CustomerRepository(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, TenancyMongoStoreClientType.TENANCY, Customer.class, validationService);
  }
}
