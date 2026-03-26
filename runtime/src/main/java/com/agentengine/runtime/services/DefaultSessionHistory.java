package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.SessionEvent;
import com.agentengine.runtime.actor.SessionHistory;
import com.agentengine.runtime.projection.SessionEventRecord;
import com.mongodb.client.MongoCollection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.util.ArrayList;
import java.util.List;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Sorts.ascending;

/**
 * MongoDB-backed implementation of SessionHistory. Reads from the
 * session_events collection materialized by SessionHistoryProjectionHandler. No
 * actor ask, no blocking.
 */
@Singleton
public class DefaultSessionHistory implements SessionHistory {

  private final MongoCollection<SessionEventRecord> collection;

  @Inject
  public DefaultSessionHistory(final MongoCollection<SessionEventRecord> collection) {
    this.collection = collection;
  }

  @Override
  public List<SessionEvent> events(final String sessionId) {
    return collection.find(eq("sessionId", sessionId)).sort(ascending("sequence")).map(SessionEventRecord::event).into(new ArrayList<>());
  }
}
