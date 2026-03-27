package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.SessionHistoryService;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.common.JsonUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.query.Sort;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Sorts;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.bson.Document;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;

/**
 * MongoDB-backed SessionHistory reading from the session_events projection
 * collection materialized by SessionHistoryProjectionHandler.
 *
 * <p>
 * Reads are eventually consistent: if the projection has not caught up to the
 * latest TurnCommitted facts, recent events may be absent. Callers must not
 * rely on this view for real-time decisions during an active run.
 */
@Singleton
public class SessionHistoryServiceImpl extends AbstractMongoRepository<SessionEvent> implements SessionHistoryService {

  @Inject
  public SessionHistoryServiceImpl(final MongoClientFactory mongoClientFactory, final ValidationService validationService) {
    super(mongoClientFactory, AssetClass.SESSION_EVENT, SessionEvent.class, validationService);
  }

  @Override
  public List<SessionEvent> events(final String sessionId) {
    final Query query = new Query().withFilter(Filters.eq(SessionEvent.FIELD_SESSION_ID, sessionId)).withSort(new Sort(BaseEntity.FIELD_CREATED_TIME, Sort.Order.ASC));
    return findByQuery(query).getItems();
  }
}
