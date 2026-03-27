package com.agentengine.runtime.projection;

import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.runtime.actor.SessionFact;
import com.agentengine.util.common.JsonUtils;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.pekko.Done;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.javadsl.Handler;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Pekko Projection handler that materializes TurnCommitted facts into MongoDB.
 * Only processes TurnCommitted facts — all others are skipped. Writes are
 * idempotent: the unique index on (sessionId, sequence) prevents duplicates on
 * projection replay or restart.
 */
public final class SessionHistoryProjectionHandler extends Handler<EventEnvelope<SessionFact>> {

  private static final Logger LOG = LoggerFactory.getLogger(SessionHistoryProjectionHandler.class);

  private final MongoCollection<Document> collection;

  public SessionHistoryProjectionHandler(final MongoCollection<Document> collection) {
    this.collection = collection;
  }

  @Override
  public CompletionStage<Done> process(final EventEnvelope<SessionFact> envelope) {
    return switch (envelope.event()) {
      case SessionFact.TurnCommitted fact -> writeTurnEvents(fact);
      default -> CompletableFuture.completedFuture(Done.getInstance());
    };
  }

  private CompletionStage<Done> writeTurnEvents(final SessionFact.TurnCommitted fact) {
    final List<SessionEvent> events = fact.events();
    final List<Document> docs = IntStream.range(0, events.size()).mapToObj(i -> toDocument(events.get(i), fact.startSequence() + i))
        .toList();

    try {
      for (final Document doc : docs) {
        collection.replaceOne(and(eq("sessionId", doc.getString("sessionId")), eq("sequence", doc.getLong("sequence"))), doc,
            new ReplaceOptions().upsert(true));
      }
      LOG.debug("Projected {} events for session {}", docs.size(), docs.isEmpty() ? "?" : docs.getFirst().getString("sessionId"));
    } catch (final Exception e) {
      LOG.error("Failed to write turn events to projection", e);
      return CompletableFuture.failedFuture(e);
    }
    return CompletableFuture.completedFuture(Done.getInstance());
  }

  private static Document toDocument(final SessionEvent event, final long sequence) {
    return new Document().append("sessionId", event.getSessionId()).append("sequence", sequence).append("eventJson", JsonUtils.toJson(event));
  }
}
