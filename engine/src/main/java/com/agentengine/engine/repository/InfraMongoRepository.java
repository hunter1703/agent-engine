package com.agentengine.engine.repository;

import com.agentengine.util.common.CollectionUtils;
import com.agentengine.engine.infra.InfraConfig;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Page;
import com.agentengine.util.common.query.PaginatedResult;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class InfraMongoRepository extends AbstractMongoRepository<InfraConfig> {

  @Inject
  public InfraMongoRepository(
      final MongoClientFactory mongoClientFactory, ValidationService validationService) {
    super(mongoClientFactory, "INFRA", "InfraConfig", InfraConfig.class, validationService);
  }

  @SuppressWarnings("unchecked")
  public <T extends InfraConfig> T findOneByType(String type) {
    final Query query = new Query().withFilter(Filters.eq("type", type)).withPage(new Page(0, 1));
    // noinspection unchecked
    final PaginatedResult<T> result = (PaginatedResult<T>) findByQuery(query);
    return CollectionUtils.getFirst(result.getItems());
  }
}
