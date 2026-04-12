package com.agentengine.runtime.services;

import static com.mongodb.client.model.Filters.eq;

import com.agentengine.runtime.api.services.SessionHistoryService;
import com.agentengine.runtime.session.SessionActor;
import com.agentengine.runtime.session.events.StartedFact;
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
import java.util.Comparator;
import java.util.List;
import org.apache.pekko.persistence.query.EventEnvelope;
import org.apache.pekko.persistence.typed.PersistenceId;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Journal-backed SessionHistory reader over committed {@link TurnCommittedFact} facts.
 *
 * <p>Reads are strongly consistent with the durable actor journal.
 */
@Singleton
public class SessionHistoryServiceImpl extends AbstractJournalReadRepository implements SessionHistoryService {

    private static final Logger LOG = LoggerFactory.getLogger(SessionHistoryServiceImpl.class);

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
            LOG.warn("Session not found: {}", sessionId);
            return List.of();
        }
        final List<SessionEvent> sessionEvents = new ArrayList<>(SessionEventUtils.toSessionEvents(
                session.getRootSessionId(), session.getParentSessionId(), sessionId, _getEvents(session), 0L));

        // Sort by sequence number to ensure correct chronological order
        // The user message is prepended during commit but has sequence=0, so it must be sorted first
        sessionEvents.sort(Comparator.comparingLong(SessionEvent::getSequence));
        return sessionEvents;
    }

    @Override
    public List<SessionEvent> getAllSessionEvents(final String sessionId) {
        final AgentSession rootSession = findSession(sessionId);
        if (rootSession == null) {
            return List.of();
        }

        final List<SessionEvent> allEvents = new ArrayList<>();

        final List<AgentSession> childSessions =
                sessions.find(eq(AgentSession.FIELD_ROOT_SESSION_ID, sessionId)).into(new ArrayList<>());

        for (final AgentSession childSession : childSessions) {
            allEvents.addAll(getSessionEvents(childSession.getId()));
        }

        // Sort by sequence number to ensure correct chronological order
        // Sequence numbers are assigned during event creation and ensure proper ordering
        allEvents.sort(Comparator.comparingLong(SessionEvent::getSequence));

        return allEvents;
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
        LOG.info(
                "[USER_MESSAGE_TRACE][{}] SessionHistoryService._getEvents() reading journal for persistenceId: {}",
                session.getId(),
                persistenceId);

        final List<Object> allJournalEvents = getJournalEvents(persistenceId).stream()
                .map(EventEnvelope::event)
                .toList();

        LOG.info(
                "[USER_MESSAGE_TRACE][{}] Total journal events (all types): {}",
                session.getId(),
                allJournalEvents.size());

        for (int i = 0; i < allJournalEvents.size(); i++) {
            final Object evt = allJournalEvents.get(i);
            LOG.info(
                    "[USER_MESSAGE_TRACE][{}] Journal event #{}: type={}",
                    session.getId(),
                    i,
                    evt.getClass().getSimpleName());
            if (evt instanceof StartedFact startedFact) {
                LOG.info(
                        "[USER_MESSAGE_TRACE][{}] StartedFact contains message: '{}'",
                        session.getId(),
                        startedFact.getMessage().getRecord());
            }
        }

        final List<Event> events = allJournalEvents.stream()
                .filter(event -> event instanceof TurnCommittedFact)
                .map(event -> {
                    TurnCommittedFact turnCommittedFact = (TurnCommittedFact) event;
                    LOG.info(
                            "[USER_MESSAGE_TRACE][{}] Found TurnCommittedFact with {} events",
                            session.getId(),
                            turnCommittedFact.getEvents().size());
                    return turnCommittedFact.getEvents();
                })
                .flatMap(List::stream)
                .toList();

        LOG.info(
                "[USER_MESSAGE_TRACE][{}] Total ADK events extracted from TurnCommittedFacts: {}",
                session.getId(),
                events.size());

        if (!events.isEmpty()) {
            LOG.info(
                    "[USER_MESSAGE_TRACE][{}] First ADK event author: {}, content: {}",
                    session.getId(),
                    events.get(0).author(),
                    events.get(0).content().map(c -> c.text()).orElse("<no-content>"));
        }

        return events;
    }
}
