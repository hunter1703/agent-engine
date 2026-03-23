package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.SessionEvent;
import com.agentengine.runtime.actor.SessionHistory;
import com.agentengine.runtime.repository.SessionRepository;
import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.sessions.*;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Singleton;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

@Singleton
public class MongoSessionService extends SessionRepository implements BaseSessionService {
    private static final Logger LOG = LoggerFactory.getLogger(MongoSessionService.class);
    private final SessionHistory sessionHistory;

    public MongoSessionService(final MongoClientFactory mongoClientFactory,
                               final ValidationService validationService,
                               final SessionHistory sessionHistory) {
        super(mongoClientFactory, validationService);
        this.sessionHistory = sessionHistory;
    }

    @Override
    public Single<Session> createSession(final String agentId, final String userId, @Nullable final ConcurrentMap<String, Object> state, @Nullable final String sessionId) {
        final String resolvedSessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
        return createSession(agentId, resolvedSessionId, userId, null, null, null, state);
    }

    public Single<Session> createSession(final String agentId,
                                         final String sessionId,
                                         final String userId,
                                         String rootSessionId,
                                         final String parentSessionId,
                                         final String spawnedByAgentId, final ConcurrentMap<String, Object> state) {
        if (StringUtils.isBlank(parentSessionId) && StringUtils.isNotBlank(rootSessionId) && !Objects.equals(rootSessionId, sessionId)) {
            throw new IllegalArgumentException("Cannot have root session if there is no parent session");
        }
        final AgentSession existing = findById(sessionId);
        if (existing != null) {
            return Single.just(SessionUtils.toSession(existing));
        }
        final ConcurrentMap<String, Object> initialState = state == null ? SessionUtils.buildInitialState() : new ConcurrentHashMap<>(state);
        final Session session = Session.builder(sessionId).appName(agentId).userId(userId)
                .state(initialState).events(new ArrayList<>()).lastUpdateTime(Instant.now()).build();
        final AgentSession agentSession = new AgentSession(sessionId, agentId, initialState);
        rootSessionId = StringUtils.isBlank(rootSessionId) ? sessionId : rootSessionId;
        AgentSession parentSession = null;
        if (StringUtils.isNotBlank(parentSessionId)) {
            parentSession = findById(parentSessionId);
        }
        AgentSession rootSession = null;
        if (StringUtils.isNotBlank(rootSessionId)) {
            rootSession = Objects.equals(parentSessionId, rootSessionId) ? parentSession : findById(rootSessionId);
        }
        agentSession.setRootSessionId(rootSessionId);
        agentSession.setRootAgentId(getRootAgentId(agentId, rootSession, sessionId));
        agentSession.setParentSessionId(parentSessionId);
        agentSession.setSpawnedByAgentId(spawnedByAgentId);
        agentSession.setDepth(resolveDepth(parentSession));
        agentSession.setStatus(AgentSession.AgentSessionStatus.PENDING_INIT.name());
        insert(agentSession);
        return Single.just(session);
    }

    public void updateSessionStatus(final String sessionId, final AgentSession.AgentSessionStatus status) {
        final List<Operation> operations = new ArrayList<>();
        operations.add(Operation.set(AgentSession.FIELD_STATUS, status.name()));
        if (status.isTerminal()) {
            operations.add(Operation.set(AgentSession.FIELD_CLOSED_AT, System.currentTimeMillis()));
        }
        update(sessionId, new Update(operations));
    }

    @Override
    public Maybe<Session> getSession(final String agentId, final String userId, final String sessionId,
                                     final Optional<GetSessionConfig> config) {
        final AgentSession agentSession = findById(sessionId);
        if (agentSession == null) {
            return Maybe.empty();
        }
        final List<Event> events = filterEvents(loadCommittedEvents(agentSession), config.orElse(null));
        return Maybe.just(SessionUtils.toSession(agentSession, events));
    }

    @Override
    public Single<ListSessionsResponse> listSessions(final String agentId, final String userId) {
        final List<Session> sessions = new ArrayList<>();

        final Filter filter = Filters.and(Filters.eq("agentId", agentId), Filters.eq("userId", userId));
        Query query = new Query().withFilter(filter);
        findByQuery(query).getItems().forEach(agentSession -> {
            final Session stored = SessionUtils.toSession(agentSession);
            sessions.add(
                    Session.builder(stored.id()).appName(stored.appName()).userId(stored.userId()).lastUpdateTime(stored.lastUpdateTime()).build());
        });
        return Single.just(ListSessionsResponse.builder().sessions(sessions).build());
    }

    @Override
    public Completable deleteSession(final String appName, final String userId, final String sessionId) {
        LOG.debug("deleteSession - appName={} userId={} sessionId={}", appName, userId, sessionId);
        final boolean deleted = deleteById(sessionId);
        if (!deleted) {
            LOG.debug("Session delete did not match any document - session_id={}", sessionId);
        }
        return Completable.complete();
    }

    @Override
    public Single<ListEventsResponse> listEvents(final String appName, final String userId, final String sessionId) {
        LOG.debug("listEvents - appName={} userId={} sessionId={}", appName, userId, sessionId);
        final AgentSession agentSession = findById(sessionId);
        if (agentSession == null) {
            return Single.just(ListEventsResponse.builder().build());
        }
        return Single.just(ListEventsResponse.builder().events(loadCommittedEvents(agentSession)).build());
    }

    @Override
    public Single<Event> appendEvent(final Session session, final Event event) {
        Functions.populateClientFunctionCallId(event);
        return BaseSessionService.super.appendEvent(session, event).doOnSuccess(_ -> {
            if (!event.partial().orElse(false)) {
                final Operation setState = Operation.set(AgentSession.FIELD_STATE, new HashMap<>(CollectionUtils.nullSafeMap(session.state())));
                final Operation setLastUpdateTime = Operation.set(BaseEntity.FIELD_UPDATED_TIME, session.lastUpdateTime().toEpochMilli());
                update(session.id(), Update.of(setState, setLastUpdateTime));
            }
        });
    }

    private List<Event> loadCommittedEvents(final AgentSession agentSession) {
        if (agentSession == null) {
            return List.of();
        }
        final List<SessionEvent> committedEvents = sessionHistory.events(agentSession.getAgentId(), agentSession.getId());
        if (CollectionUtils.isEmpty(committedEvents)) {
            return List.of();
        }
        final List<Event> events = new ArrayList<>(committedEvents.size());
        for (final SessionEvent committedEvent : committedEvents) {
            final Event.Builder builder = Event.builder()
                    .id(committedEvent.id())
                    .invocationId(committedEvent.runId())
                    .author(committedEvent.author())
                    .timestamp(committedEvent.timestamp());
            if (committedEvent.content() != null) {
                builder.content(committedEvent.content());
            }
            if (committedEvent.partial() != null) {
                builder.partial(committedEvent.partial());
            }
            if (committedEvent.turnComplete() != null) {
                builder.turnComplete(committedEvent.turnComplete());
            }
            if (committedEvent.finishReason() != null) {
                builder.finishReason(committedEvent.finishReason());
            }
            final Map<String, Object> metadata = committedEvent.metadata();
            if (CollectionUtils.isNotEmpty(metadata)) {
                final EventActions actions = EventActions.builder().stateDelta(new ConcurrentHashMap<>(metadata)).build();
                builder.actions(actions);
            }
            events.add(builder.build());
        }
        return events;
    }

    private static List<Event> filterEvents(final List<Event> events, final GetSessionConfig config) {
        if (events == null || events.isEmpty()) {
            return List.of();
        }
        final List<Event> result = new ArrayList<>(events);
        if (config == null) {
            return result;
        }
        if (config.numRecentEvents().isPresent()) {
            final int numRecentEvents = config.numRecentEvents().orElse(0);
            if (result.size() > numRecentEvents) {
                return result.subList(result.size() - numRecentEvents, result.size());
            }
            return result;
        }
        if (config.afterTimestamp().isPresent()) {
            final Instant threshold = config.afterTimestamp().get();
            return result.stream().filter(event -> !Instant.ofEpochMilli(event.timestamp()).isBefore(threshold)).collect(Collectors.toList());
        }
        return result;
    }

    private static int resolveDepth(final AgentSession parent) {
        return parent == null ? 1 : parent.getDepth() + 1;
    }

    private static String getRootAgentId(final String agentId, final AgentSession root, final String sessionId) {
        if (root == null || root.getId().equals(sessionId)) {
            return agentId;
        }
        return root.getAgentId();
    }
}
