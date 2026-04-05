package com.agentengine.util.pekko.persistence;

import com.agentengine.util.common.LazyLoader;
import com.agentengine.util.pekko.ActorSystemProvider;
import java.util.List;
import org.apache.pekko.actor.typed.ActorSystem;
import org.apache.pekko.persistence.jdbc.query.javadsl.JdbcReadJournal;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.query.PersistenceQuery;
import org.apache.pekko.stream.javadsl.Sink;

/** Base support for read paths backed directly by the Pekko persistence journal. */
public abstract class AbstractJournalReadRepository {

    private final ActorSystemProvider actorSystemProvider;
    private final LazyLoader<JdbcReadJournal> readJournal;

    protected AbstractJournalReadRepository(final ActorSystemProvider actorSystemProvider) {
        this.actorSystemProvider = actorSystemProvider;
        this.readJournal = new LazyLoader<>(() -> PersistenceQuery.get(actorSystemProvider.system())
                .getReadJournalFor(JdbcReadJournal.class, JdbcReadJournal.Identifier()));
    }

    protected final List<EventEnvelope> getJournalEvents(final String persistenceId) {
        if (persistenceId == null || persistenceId.isBlank()) {
            return List.of();
        }
        final ActorSystem<?> system = actorSystemProvider.system();
        return readJournal
                .get()
                .currentEventsByPersistenceId(persistenceId, 0L, Long.MAX_VALUE)
                .runWith(Sink.seq(), system)
                .toCompletableFuture()
                .join();
    }
}
