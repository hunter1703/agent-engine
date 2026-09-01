package com.agentengine.agent.core.session;

import com.agentengine.agent.core.session.events.RollbackFact;
import com.agentengine.agent.core.session.events.SessionFact;
import com.agentengine.agent.core.session.events.TurnCommittedFact;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.persistence.AbstractReadJournal;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import org.apache.pekko.persistence.typed.PersistenceId;

@Singleton
public class SessionActorJournal extends AbstractReadJournal<SessionFact> {

  @Inject
  public SessionActorJournal(final ActorSystemProvider actorSystemProvider) {
    super(actorSystemProvider, SessionFact.class);
  }

  public List<SessionFact> readSessionEvents(
      final String sessionId, final Predicate<SessionFact> predicate) {
    final String persistenceId = PersistenceId.of(SessionActor.TYPE_KEY.name(), sessionId).id();
    return readEvents(persistenceId, predicate);
  }

  /**
   * Returns the turn IDs still visible for {@code sessionId} after folding in every {@link
   * RollbackFact}: a rollback discards the turns committed under its {@code runId} and everything
   * committed after them. A rollback whose {@code runId} committed no turns at all (e.g. a run
   * rolled back before it produced one) is a no-op here, rather than discarding unrelated turns.
   */
  public List<String> getCommittedTurnIds(final String sessionId) {
    final List<SessionFact> facts =
        readSessionEvents(
            sessionId, fact -> fact instanceof TurnCommittedFact || fact instanceof RollbackFact);
    final List<TurnCommittedFact> survivingTurns = new ArrayList<>();
    for (final SessionFact fact : facts) {
      if (fact instanceof TurnCommittedFact committed) {
        survivingTurns.add(committed);
      } else if (fact instanceof RollbackFact rollback) {
        String rollbackRunId = rollback.getRunId();
        for (int i = 0; i < survivingTurns.size(); i++) {
          if (Objects.equals(survivingTurns.get(i).getRunId(), rollbackRunId)) {
            survivingTurns.subList(i, survivingTurns.size()).clear();
            break;
          }
        }
      }
    }
    return survivingTurns.stream().map(TurnCommittedFact::getTurnId).toList();
  }
}
