package com.agentengine.engine.repository;

import com.agentengine.engine.api.query.Filters;
import com.agentengine.engine.api.query.Page;
import com.agentengine.engine.api.query.PaginatedResult;
import com.agentengine.engine.api.query.Query;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.infra.InfraConfig;
import jakarta.inject.Singleton;

@Singleton
public class InfraMongoRepository extends AbstractMongoRepository<InfraConfig> {

  public InfraMongoRepository(final MongoClientFactory mongoClientFactory) {
    super(mongoClientFactory, "INFRA", "InfraConfig", InfraConfig.class);
  }

  @SuppressWarnings("unchecked")
  public <T extends InfraConfig> T findOneByType(String type) {
    final Query query = new Query().withFilter(Filters.eq("type", type)).withPage(new Page(0, 1));
    // noinspection unchecked
    final PaginatedResult<T> result = (PaginatedResult<T>) findByQuery(query);
    return CollectionUtils.getFirst(result.getItems());
  }
}
