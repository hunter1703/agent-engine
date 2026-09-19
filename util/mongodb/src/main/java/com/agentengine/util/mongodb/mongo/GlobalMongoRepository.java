package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.validation.ValidationService;

public abstract class GlobalMongoRepository<T extends BaseEntity>
    extends AbstractMongoRepository<T> {

  public GlobalMongoRepository(
      final MongoClientFactory mongoClientFactory,
      final MongoStoreClientType clientType,
      final Class<T> entityClass,
      final ValidationService validationService) {
    super(mongoClientFactory, clientType, entityClass, validationService);
  }

  @Override
  protected String database() {
    return clientType.name();
  }

  @Override
  protected Integer customerId() {
    return null;
  }
}
