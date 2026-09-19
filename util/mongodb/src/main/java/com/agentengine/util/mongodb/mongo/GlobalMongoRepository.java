package com.agentengine.util.mongodb.mongo;

import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.validation.ValidationService;

public abstract class GlobalMongoRepository<T extends BaseEntity>
    extends AbstractMongoRepository<T> {

  public GlobalMongoRepository(
      final MongoClientFactory mongoClientFactory,
      final String store,
      final Class<T> entityClass,
      final ValidationService validationService) {
    super(mongoClientFactory, store, entityClass, validationService);
  }

  @Override
  protected String database() {
    return store;
  }

  @Override
  protected Integer customerId() {
    return null;
  }
}
