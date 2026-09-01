package com.agentengine.util.pekko.persistence;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.PekkoSerializable;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import org.apache.pekko.persistence.jdbc.query.javadsl.JdbcReadJournal;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.PersistenceQuery;
import org.apache.pekko.stream.javadsl.Sink;

/**
 * Base support for read paths backed directly by the Pekko persistence journal of facts {@code T}.
 */
public abstract class AbstractReadJournal<T extends PekkoSerializable> {

  private static final int QUERY_TIMEOUT_SECONDS = 30;

  private final ActorSystemProvider actorSystemProvider;
  private final Class<T> entityClass;
  private final LazyLoader<JdbcReadJournal> readJournal;

  protected AbstractReadJournal(
      final ActorSystemProvider actorSystemProvider, final Class<T> entityClass) {
    this.actorSystemProvider = actorSystemProvider;
    this.entityClass = entityClass;
    this.readJournal =
        new LazyLoader<>(
            () ->
                PersistenceQuery.get(actorSystemProvider.system())
                    .getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier()));
  }

  protected final List<T> readEvents(final String persistenceId, final Predicate<T> predicate) {
    if (persistenceId == null || persistenceId.isBlank()) {
      return List.of();
    }
    return readJournal
        .get()
        .currentEventsByPersistenceId(persistenceId, 0L, Long.MAX_VALUE)
        .runWith(Sink.seq(), actorSystemProvider.system())
        .toCompletableFuture()
        .orTimeout(QUERY_TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .join()
        .stream()
        .map(EventEnvelope::event)
        .map(entityClass::cast)
        .filter(predicate)
        .toList();
  }
}
