package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.SessionEvent;
import com.agentengine.runtime.actor.SessionHistory;
import com.agentengine.runtime.repository.SessionRepository;
import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Lightweight ADK session service backed by the Pekko Projection.
 *
 * <p>
 * {@code appendEvent} is not overridden — in-memory only. The actor owns event
 * persistence via {@code TurnCommitted} facts. {@code getSession} reads
 * committed events from the projection collection.
 */
@Singleton
public class ProjectionBackedSessionService extends SessionRepository implements BaseSessionService {

  private static final Logger LOG = LoggerFactory.getLogger(ProjectionBackedSessionService.class);

  private final SessionHistory sessionHistory;

  @Inject
  public ProjectionBackedSessionService(final MongoClientFactory mongoClientFactory, final ValidationService validationService,
      final SessionHistory sessionHistory) {
    super(mongoClientFactory, validationService);
    this.sessionHistory = sessionHistory;
  }

  @Override
  public Single<Session> createSession(final String agentId, final String userId, final ConcurrentMap<String, Object> state,
      final String sessionId) {
    final var resolvedId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    return createSession(agentId, resolvedId, userId, null, null, null, state);
  }

  public Single<Session> createSession(final String agentId, final String sessionId, final String userId, final String rootSessionId,
      final String parentSessionId, final String spawnedByAgentId, final ConcurrentMap<String, Object> state) {
    final var existing = findById(sessionId);
    if (existing != null) {
      return Single.just(SessionUtils.toSession(existing));
    }
    final var initialState = state == null ? SessionUtils.buildInitialState() : new ConcurrentHashMap<>(state);
    final var session = Session.builder(sessionId).appName(agentId).userId(userId).state(initialState).events(new ArrayList<>())
        .lastUpdateTime(Instant.now()).build();
    final var agentSession = new AgentSession(sessionId, agentId, initialState);
    final var resolvedRoot = StringUtils.isBlank(rootSessionId) ? sessionId : rootSessionId;
    final AgentSession parentSession = StringUtils.isNotBlank(parentSessionId) ? findById(parentSessionId) : null;
    AgentSession rootSession = null;
    if (StringUtils.isNotBlank(resolvedRoot)) {
      rootSession = Objects.equals(parentSessionId, resolvedRoot) ? parentSession : findById(resolvedRoot);
    }
    agentSession.setRootSessionId(resolvedRoot);
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

  /**
   * Flush session variables (state map) to MongoDB. Called by the actor at turn
   * boundaries.
   */
  public void flushSessionState(final String sessionId, final Map<String, Object> state) {
    update(sessionId, Update.of(Operation.set(AgentSession.FIELD_STATE, new HashMap<>(state)),
        Operation.set(BaseEntity.FIELD_UPDATED_TIME, Instant.now().toEpochMilli())));
  }

  @Override
  public Maybe<Session> getSession(final String agentId, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    final var agentSession = findById(sessionId);
    if (agentSession == null)
      return Maybe.empty();
    final var committedEvents = sessionHistory.events(sessionId);
    final var adkEvents = filterEvents(toAdkEvents(committedEvents), config.orElse(null));
    return Maybe.just(SessionUtils.toSession(agentSession, adkEvents));
  }

  @Override
  public Single<ListSessionsResponse> listSessions(final String agentId, final String userId) {
    final List<Session> sessions = new ArrayList<>();
    findByQuery(new com.agentengine.util.common.query.Query()).getItems().forEach(s -> sessions.add(SessionUtils.toSession(s)));
    return Single.just(ListSessionsResponse.builder().sessions(sessions).build());
  }

  @Override
  public Completable deleteSession(final String appName, final String userId, final String sessionId) {
    deleteById(sessionId);
    return Completable.complete();
  }

  @Override
  public Single<ListEventsResponse> listEvents(final String appName, final String userId, final String sessionId) {
    final var agentSession = findById(sessionId);
    if (agentSession == null) {
      return Single.just(ListEventsResponse.builder().build());
    }
    return Single.just(ListEventsResponse.builder().events(toAdkEvents(sessionHistory.events(sessionId))).build());
  }

  // appendEvent is intentionally NOT overridden.
  // BaseSessionService default: in-memory mutation only
  // (session.events().add(event),
  // state merge). The actor owns event persistence via TurnCommitted facts.

  // ── Helpers ──────────────────────────────────────────────────────────────

  private static List<Event> toAdkEvents(final List<SessionEvent> committedEvents) {
    if (CollectionUtils.isEmpty(committedEvents))
      return List.of();
    final List<Event> events = new ArrayList<>(committedEvents.size());
    for (final var se : committedEvents) {
      final var builder = Event.builder().id(se.id()).invocationId(se.runId()).author(se.author()).timestamp(se.timestamp());
      if (se.content() != null)
        builder.content(se.content());
      if (se.partial() != null)
        builder.partial(se.partial());
      if (se.turnComplete() != null)
        builder.turnComplete(se.turnComplete());
      if (se.finishReason() != null)
        builder.finishReason(se.finishReason());
      if (CollectionUtils.isNotEmpty(se.metadata())) {
        builder.actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(se.metadata())).build());
      }
      events.add(builder.build());
    }
    return events;
  }

  private static List<Event> filterEvents(final List<Event> events, final GetSessionConfig config) {
    if (events == null || events.isEmpty())
      return List.of();
    final List<Event> result = new ArrayList<>(events);
    if (config == null)
      return result;
    if (config.numRecentEvents().isPresent()) {
      final int n = config.numRecentEvents().get();
      return result.size() > n ? result.subList(result.size() - n, result.size()) : result;
    }
    if (config.afterTimestamp().isPresent()) {
      final var threshold = config.afterTimestamp().get();
      return result.stream().filter(e -> !Instant.ofEpochMilli(e.timestamp()).isBefore(threshold)).collect(Collectors.toList());
    }
    return result;
  }

  private static int resolveDepth(final AgentSession parent) {
    return parent == null ? 1 : parent.getDepth() + 1;
  }

  private static String getRootAgentId(final String agentId, final AgentSession root, final String sessionId) {
    if (root == null || root.getId().equals(sessionId))
      return agentId;
    return root.getAgentId();
  }
}
