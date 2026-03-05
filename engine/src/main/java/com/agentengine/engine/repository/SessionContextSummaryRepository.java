package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.session.SessionContextSummary;
import jakarta.inject.Singleton;

@Singleton
public class SessionContextSummaryRepository extends AbstractMongoRepository<SessionContextSummary> {

  public SessionContextSummaryRepository(final MongoClientFactory mongoClientFactory) {
    super(mongoClientFactory, "SessionContextSummary", SessionContextSummary.class);
  }
}
