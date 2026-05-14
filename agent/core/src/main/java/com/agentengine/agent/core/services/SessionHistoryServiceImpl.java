package com.agentengine.agent.core.services;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.or;

import com.agentengine.agent.api.services.SessionHistoryService;
import com.agentengine.agent.core.session.SessionActor;
import com.agentengine.agent.core.session.events.CompletedFact;
import com.agentengine.agent.core.session.events.RollbackFact;
import com.agentengine.agent.core.session.events.TurnCommittedFact;
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
import java.util.*;
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
        final String persistenceId =
                PersistenceId.of(SessionActor.TYPE_KEY.name(), session.getId()).id();
        final Deque<Event> events = new LinkedList<>();
        for (final Object fact : getJournalEvents(persistenceId).stream()
                .map(EventEnvelope::event)
                .toList()) {
            if (fact instanceof TurnCommittedFact committed) {
                events.addAll(committed.getEvents());
            } else if (fact instanceof RollbackFact rollback) {
                final String runId = rollback.getRunId();
                boolean foundRun = false;
                while (true) {
                    if (!foundRun) {
                        foundRun = Objects.equals(events.getLast().invocationId(), runId);
                    } else if (!Objects.equals(events.getLast().invocationId(), runId)) {
                        break;
                    }
                    events.removeLast();
                }
            }
        }
        return List.copyOf(events);
    }

    @Override
    public List<SessionEvent> getSessionEvents(final String sessionId) {
        final AgentSession session = findSession(sessionId);
        if (session == null) {
            LOG.warn("Session not found: {}", sessionId);
            return List.of();
        }
        return scanJournal(session);
    }

    @Override
    public List<SessionEvent> getAllSessionEvents(final String sessionId) {
        final AgentSession session = findSession(sessionId);
        if (session == null) {
            return List.of();
        }

        final List<SessionEvent> allEvents = new ArrayList<>();

        final List<AgentSession> childSessions = sessions.find(
                        or(eq(AgentSession.FIELD_ROOT_SESSION_ID, sessionId), eq(AgentSession.FIELD_ID, sessionId)))
                .into(new ArrayList<>());

        for (final AgentSession childSession : childSessions) {
            allEvents.addAll(getSessionEvents(childSession.getId()));
        }

        final String rootSessionId = session.getRootSessionId();
        /*
         First sort by timestamp for rough chronological order
         Then, sort such that root session events come before non-root (for equal timestamps)
         Then, sort by sequence for events belonging to same sessions
        */
        allEvents.sort(Comparator.comparingLong(SessionEvent::getTimestamp)
                .thenComparing(SessionEvent::getSessionId, (one, two) -> {
                    if (Objects.equals(rootSessionId, one) && !Objects.equals(rootSessionId, two)) {
                        return -1;
                    } else if (Objects.equals(rootSessionId, two) && !Objects.equals(rootSessionId, one)) {
                        return 1;
                    } else {
                        return one.compareTo(two);
                    }
                })
                .thenComparingLong(SessionEvent::getSequence));

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

    /**
     * Reads the journal for one session and returns its events in chronological order.
     *
     * <p>Facts are processed sequentially: each {@link TurnCommittedFact} contributes its ADK
     * events (assigned monotonically increasing sequence numbers), and each {@link CompletedFact}
     * with a non-blank error injects a {@link SessionEvent#error} immediately after the turn that
     * failed. This correctly handles sessions with multiple runs where more than one run failed.
     */
    private @NonNull List<SessionEvent> scanJournal(final AgentSession session) {
        final String persistenceId =
                PersistenceId.of(SessionActor.TYPE_KEY.name(), session.getId()).id();
        LOG.info(
                "[USER_MESSAGE_TRACE][{}] scanJournal() reading journal for persistenceId: {}",
                session.getId(),
                persistenceId);

        final List<Object> facts = getJournalEvents(persistenceId).stream()
                .map(EventEnvelope::event)
                .toList();

        LOG.info("[USER_MESSAGE_TRACE][{}] Total journal facts: {}", session.getId(), facts.size());

        final String rootSessionId = session.getRootSessionId();
        final String parentSessionId = session.getParentSessionId();
        final String sessionId = session.getId();

        final List<SessionEvent> sessionEvents = new ArrayList<>();
        long seq = 0;

        for (final Object fact : facts) {
            if (fact instanceof TurnCommittedFact turnCommitedFact) {
                LOG.info(
                        "[USER_MESSAGE_TRACE][{}] TurnCommittedFact with {} events at seq={}",
                        sessionId,
                        turnCommitedFact.getEvents().size(),
                        seq);
                sessionEvents.addAll(SessionEventUtils.toSessionEvents(
                        rootSessionId, parentSessionId, sessionId, turnCommitedFact.getEvents(), seq));
                seq += turnCommitedFact.getEvents().size();
            } else if (fact instanceof CompletedFact completed && StringUtils.isNotEmpty(completed.getError())) {
                LOG.info(
                        "[USER_MESSAGE_TRACE][{}] CompletedFact with error at seq={}: {}",
                        sessionId,
                        seq,
                        completed.getError());
                sessionEvents.add(SessionEvent.error(rootSessionId, sessionId, completed.getError(), seq++));
            }
        }

        LOG.info("[USER_MESSAGE_TRACE][{}] scanJournal() produced {} session events", sessionId, sessionEvents.size());
        return sessionEvents;
    }
}
