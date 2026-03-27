package com.agentengine.runtime.services;

import com.agentengine.runtime.actor.SessionHistoryService;
import com.agentengine.util.agents.beans.SessionEvent;
import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.CollectionUtils;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.beans.BaseEntity;
import com.agentengine.util.common.beans.NamedEntity;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
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
public class ProjectionBackedSessionService extends AbstractMongoRepository<AgentSession> implements BaseSessionService {

  private final SessionHistoryService sessionHistoryService;
  @Inject
  public ProjectionBackedSessionService(final MongoClientFactory mongoClientFactory, final ValidationService validationService,
      final SessionHistoryService sessionHistoryService) {
    super(mongoClientFactory, AssetClass.AGENT_SESSION, AgentSession.class, validationService);
    this.sessionHistoryService = sessionHistoryService;
  }

  @Override
  public Single<Session> createSession(final String agentId, final String userId, final ConcurrentMap<String, Object> state, String sessionId) {
    sessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    return createSession(agentId, sessionId, userId, null, null, null, state);
  }

  public Single<Session> createSession(final String agentId, final String sessionId, final String userId, final String rootSessionId,
      final String parentSessionId, final String spawnedByAgentId, final ConcurrentMap<String, Object> state) {
    final AgentSession existing = findById(sessionId);
    if (existing != null) {
      return Single.just(SessionUtils.toSession(existing));
    }
    final ConcurrentMap<String, Object> initialState = state == null ? SessionUtils.buildInitialState() : new ConcurrentHashMap<>(state);
    final Session session = Session.builder(sessionId).appName(agentId).userId(userId).state(initialState).events(new ArrayList<>())
        .lastUpdateTime(Instant.now()).build();
    final AgentSession agentSession = new AgentSession(sessionId, agentId, initialState);
    final AgentSession parentSession = StringUtils.isNotBlank(parentSessionId) ? findById(parentSessionId) : null;
    agentSession.setRootSessionId(parentSession == null ? sessionId : parentSession.getRootSessionId());
    agentSession.setParentSessionId(parentSessionId);
    agentSession.setRootAgentId(parentSession == null ? agentId : parentSession.getAgentId());
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

  public boolean updateSessionTitleIfUntitled(final String sessionId, final String title) {
    if (StringUtils.isBlank(sessionId) || StringUtils.isBlank(title)) {
      return false;
    }
    final AgentSession session = findById(sessionId);
    if (session == null || !"Untitled Session".equals(session.getName())) {
      return false;
    }
    return update(sessionId, Update.of(Operation.set(NamedEntity.FIELD_NAME, title.trim()),
        Operation.set(BaseEntity.FIELD_UPDATED_TIME, System.currentTimeMillis()))) != null;
  }


  @Override
  public Maybe<Session> getSession(final String agentId, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    final AgentSession agentSession = findById(sessionId);
    if (agentSession == null)
      return Maybe.empty();
    final List<SessionEvent> committedEvents = sessionHistoryService.events(sessionId);
    final List<Event> adkEvents = filterEvents(toAdkEvents(committedEvents), config.orElse(null));
    return Maybe.just(SessionUtils.toSession(agentSession, adkEvents));
  }

  @Override
  public Single<ListSessionsResponse> listSessions(final String agentId, final String userId) {
    final List<Session> sessions = new ArrayList<>();
    findByQuery(new Query()).getItems().forEach(session -> sessions.add(SessionUtils.toSession(session)));
    return Single.just(ListSessionsResponse.builder().sessions(sessions).build());
  }

  @Override
  public Completable deleteSession(final String appName, final String userId, final String sessionId) {
    deleteById(sessionId);
    return Completable.complete();
  }

  @Override
  public Single<ListEventsResponse> listEvents(final String appName, final String userId, final String sessionId) {
    final AgentSession agentSession = findById(sessionId);
    if (agentSession == null) {
      return Single.just(ListEventsResponse.builder().build());
    }
    return Single.just(ListEventsResponse.builder().events(toAdkEvents(sessionHistoryService.events(sessionId))).build());
  }

  private static List<Event> toAdkEvents(final List<SessionEvent> committedEvents) {
    if (CollectionUtils.isEmpty(committedEvents))
      return List.of();
    final List<Event> events = new ArrayList<>(committedEvents.size());
    for (final SessionEvent sessionEvent : committedEvents) {
      final Event.Builder builder = Event.builder().id(sessionEvent.getId()).invocationId(sessionEvent.getRunId()).author(sessionEvent.getAuthor()).timestamp(sessionEvent.getTimestamp());
      if (sessionEvent.getContent() != null)
        builder.content(sessionEvent.getContent());
      if (sessionEvent.isPartial() != null)
        builder.partial(sessionEvent.isPartial());
      if (sessionEvent.isTurnComplete() != null)
        builder.turnComplete(sessionEvent.isTurnComplete());
      if (sessionEvent.getFinishReason() != null)
        builder.finishReason(sessionEvent.getFinishReason());
      if (CollectionUtils.isNotEmpty(sessionEvent.getMetadata())) {
        builder.actions(EventActions.builder().stateDelta(new ConcurrentHashMap<>(sessionEvent.getMetadata())).build());
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
      final Instant threshold = config.afterTimestamp().get();
      return result.stream().filter(event -> !Instant.ofEpochMilli(event.timestamp()).isBefore(threshold)).collect(Collectors.toList());
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
