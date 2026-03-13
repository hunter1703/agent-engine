package com.agentengine.util.ms;

import com.agentengine.util.common.CollectionUtils;
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
public class MicroServiceRepository extends AbstractMongoRepository<MicroServiceInfraConfig> {

  @Inject
  public MicroServiceRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, "INFRA", "MicroServiceConfig", MicroServiceInfraConfig.class, validationService);
  }

  public MicroServiceInfraConfig findByServerId(final String serverId) {
    final Query query = new Query().withFilter(Filters.eq("serverId", serverId)).withPage(new Page(0, 1));
    final PaginatedResult<MicroServiceInfraConfig> result = findByQuery(query);
    return CollectionUtils.getFirst(result.getItems());
  }
}
