package com.agentengine.connectors.core.repository;

import com.agentengine.connectors.core.runtime.Connection;
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
public class ConnectionRepository extends AbstractMongoRepository<Connection> {

  @Inject
  public ConnectionRepository(
      final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, "Connection", Connection.class, validationService);
  }

  public Connection findByAppName(final String appName) {
    if (appName == null || appName.isBlank()) {
      return null;
    }
    final Query query =
        new Query().withFilter(Filters.eq("appName", appName)).withPage(new Page(0, 1));
    final PaginatedResult<Connection> result = findByQuery(query);
    return CollectionUtils.getFirst(result.getItems());
  }
}
