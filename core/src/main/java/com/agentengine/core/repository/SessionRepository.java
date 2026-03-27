package com.agentengine.core.repository;

import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

@Singleton
public class SessionRepository extends AbstractMongoRepository<AgentSession> {
  @Inject
  public SessionRepository(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, AssetClass.AGENT_SESSION, AgentSession.class, validationService);
  }
}
