package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.SessionEvent;
import com.agentengine.runtime.actor.SessionHistory;
import com.agentengine.util.common.JsonUtils;
import com.mongodb.client.MongoCollection;
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
public class DefaultSessionHistory implements SessionHistory {

  private final MongoCollection<Document> collection;

  @Inject
  public DefaultSessionHistory(final MongoCollection<Document> collection) {
    this.collection = collection;
  }

  @Override
  public List<SessionEvent> events(final String sessionId) {
    return collection.find(eq("sessionId", sessionId)).sort(ascending("sequence"))
        .map(doc -> JsonUtils.fromJson(doc.getString("eventJson"), SessionEvent.class)).into(new ArrayList<>());
  }
}
