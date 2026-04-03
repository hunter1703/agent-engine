package com.agentengine.runtime.services;

import static com.mongodb.client.model.Filters.eq;

import com.agentengine.runtime.api.services.SessionHistoryService;
import com.agentengine.runtime.session.SessionActor;
import com.agentengine.runtime.session.events.TurnCommittedFact;
import com.agentengine.util.agents.SessionEventUtils;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.agentengine.util.pekko.ActorSystemProvider;
import com.agentengine.util.pekko.persistence.AbstractJournalReadRepository;
import com.google.adk.events.Event;
import com.mongodb.client.MongoCollection;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.jspecify.annotations.NonNull;

/**
 * Journal-backed SessionHistory reader over committed {@link TurnCommittedFact} facts.
 *
 * <p>Reads are strongly consistent with the durable actor journal.
 */
@Singleton
public class SessionHistoryServiceImpl extends AbstractJournalReadRepository implements SessionHistoryService {

    private final MongoCollection<AgentSession> sessions;

    @Inject
    public SessionHistoryServiceImpl(
            final ActorSystemProvider actorSystemProvider, final MongoClientFactory mongoClientFactory) {
        super(actorSystemProvider);
        this.sessions = mongoClientFactory
                .getClient()
                .getDatabase("AGENT_ENGINE")
                .getCollection(AssetClass.AGENT_SESSION, AgentSession.class);
    }

    public List<Event> getEvents(final String sessionId) {
        final AgentSession session = findSession(sessionId);
        if (session == null) {
            return List.of();
        }
        return _getEvents(session);
    }

    @Override
    public List<SessionEvent> getSessionEvents(final String sessionId) {
        final AgentSession session = findSession(sessionId);
        if (session == null) {
            return List.of();
        }
        return SessionEventUtils.toSessionEvents(
                session.getRootSessionId(), session.getParentSessionId(), sessionId, _getEvents(session), 0L);
    }

    private AgentSession findSession(final String sessionId) {
        if (StringUtils.isBlank(sessionId)) {
            return null;
        }

        final AgentSession session = sessions.find(eq("_id", sessionId)).first();
        if (session == null || StringUtils.isBlank(session.getAgentId())) {
            return null;
        }
        return session;
    }

    private @NonNull List<Event> _getEvents(final AgentSession session) {
        final String persistenceId =
                PersistenceId.of(SessionActor.TYPE_KEY.name(), session.getId()).id();
        final List<TurnCommittedFact> turns = currentEventsByPersistenceId(persistenceId, TurnCommittedFact.class);
        final List<Event> history = new ArrayList<>();
        for (final TurnCommittedFact turn : turns) {
            history.addAll(turn.getEvents());
        }
        return history;
    }
}
