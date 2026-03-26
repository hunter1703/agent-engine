package com.agentengine.runtime.projection;

import com.agentengine.runtime.actor.SessionFact;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.ReplaceOptions;
import org.apache.pekko.Done;
import org.apache.pekko.persistence.query.typed.EventEnvelope;
import org.apache.pekko.projection.javadsl.Handler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.stream.IntStream;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;

/**
 * Pekko Projection handler that materializes TurnCommitted facts into MongoDB.
 *
 * <p>
 * Only processes TurnCommitted facts — all other journal events are ignored.
 * Writes are idempotent: the unique index on (sessionId, sequence) prevents
 * duplicates on projection replay or restart.
 */
public final class SessionHistoryProjectionHandler extends Handler<EventEnvelope<SessionFact>> {

  private static final Logger LOG = LoggerFactory.getLogger(SessionHistoryProjectionHandler.class);

  private final MongoCollection<SessionEventRecord> collection;

  public SessionHistoryProjectionHandler(final MongoCollection<SessionEventRecord> collection) {
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
    final var records = IntStream.range(0, fact.events().size())
        .mapToObj(i -> new SessionEventRecord(fact.events().get(i).sessionId(), fact.startSequence() + i, fact.events().get(i))).toList();

    try {
      for (final var record : records) {
        collection.replaceOne(and(eq("sessionId", record.sessionId()), eq("sequence", record.sequence())), record,
            new ReplaceOptions().upsert(true));
      }
      LOG.debug("Projected {} events for session {}", records.size(), records.isEmpty() ? "?" : records.getFirst().sessionId());
    } catch (final Exception e) {
      LOG.error("Failed to write turn events to projection", e);
      return CompletableFuture.failedFuture(e);
    }
    return CompletableFuture.completedFuture(Done.getInstance());
  }
}
