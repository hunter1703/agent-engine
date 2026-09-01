package com.agentengine.agent.core.session;

import com.agentengine.agent.core.session.events.SessionFact;
import com.agentengine.agent.core.session.events.TurnCommittedFact;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.persistence.AbstractJournalReadRepository;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.List;
import java.util.function.Predicate;
import org.apache.pekko.persistence.typed.PersistenceId;

@Singleton
public class SessionActorJournal extends AbstractJournalReadRepository<SessionFact> {

  @Inject
  public SessionActorJournal(final ActorSystemProvider actorSystemProvider) {
    super(actorSystemProvider, SessionFact.class);
  }

  public List<SessionFact> readSessionEvents(
      final String sessionId, final Predicate<SessionFact> predicate) {
    final String persistenceId = PersistenceId.of(SessionActor.TYPE_KEY.name(), sessionId).id();
    return readEvents(persistenceId, predicate);
  }

  /** Returns the turn IDs of every {@code TurnCommittedFact} persisted for {@code sessionId}. */
  public List<String> getCommittedTurnIds(final String sessionId) {
    return readSessionEvents(sessionId, fact -> fact instanceof TurnCommittedFact).stream()
        .map(fact -> ((TurnCommittedFact) fact).getTurnId())
        .toList();
  }
}
