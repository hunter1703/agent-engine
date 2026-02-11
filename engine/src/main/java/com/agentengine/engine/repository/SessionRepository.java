package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import jakarta.inject.Singleton;

/**
 * Repository for managing Session entities
 */
@Singleton
public class SessionRepository extends AbstractMongoRepository<AgentSession> {

  public SessionRepository(MongoClientSupport mongoClientSupport) {
    super(mongoClientSupport, "Session", AgentSession.class);
  }

  public void updateTitle(String id, String title) {
    getCollection().findOneAndUpdate(Filters.eq("_id", id), Updates.set(AgentSession.FIELD_TITLE, title));
  }
}