package com.agentengine.runtime.services;

import com.agentengine.runtime.utils.SessionUtils;
import com.agentengine.util.agents.beans.session.AgentSession;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.beans.AssetClass;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.google.adk.events.Event;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * Lightweight ADK session service backed by Mongo session metadata and
 * journal-backed event history.
 *
 * <p>
 * {@code appendEvent} is not overridden — in-memory only. The actor owns event
 * persistence via {@code TurnCommittedFact} events. {@code getSession} reads
 * committed events directly from the journal.
 */
@Singleton
public class MongoSessionService extends AbstractMongoRepository<AgentSession> implements BaseSessionService {

  private final SessionHistoryServiceImpl sessionHistoryService;

  @Inject
  public MongoSessionService(final MongoClientFactory mongoClientFactory, final ValidationService validationService,
      final SessionHistoryServiceImpl sessionHistoryService) {
    super(mongoClientFactory, AssetClass.AGENT_SESSION, AgentSession.class, validationService);
    this.sessionHistoryService = sessionHistoryService;
  }

  @Override
  public Single<Session> createSession(final String agentId, final String userId, final ConcurrentMap<String, Object> state,
      String sessionId) {
    sessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    final AgentSession session = createSession(agentId, sessionId, null, state);
    return Single.just(SessionUtils.toSession(session));
  }

  public AgentSession createSession(final String agentId, final String sessionId, final String parentSessionId,
      final ConcurrentMap<String, Object> state) {
    final AgentSession existing = findById(sessionId);
    if (existing != null) {
      return existing;
    }
    final ConcurrentMap<String, Object> initialState = state == null ? SessionUtils.buildInitialState() : new ConcurrentHashMap<>(state);

    final AgentSession agentSession = new AgentSession(sessionId, agentId, initialState);
    final AgentSession parentSession = StringUtils.isNotBlank(parentSessionId) ? findById(parentSessionId) : null;
    agentSession.setRootSessionId(parentSession == null ? sessionId : resolveRootSessionId(parentSession));
    agentSession.setParentSessionId(parentSessionId);
    agentSession.setRootAgentId(parentSession == null ? agentId : resolveRootAgentId(parentSession));
    agentSession.setSpawnedByAgentId(parentSession == null ? null : parentSession.getAgentId());
    agentSession.setDepth(resolveDepth(parentSession));
    agentSession.setStatus(AgentSession.AgentSessionStatus.PENDING_INIT.name());
    insert(agentSession);
    return agentSession;
  }

  @Override
  public Maybe<Session> getSession(final String agentId, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    final AgentSession agentSession = findById(sessionId);
    if (agentSession == null)
      return Maybe.empty();
    final List<Event> adkEvents = filterEvents(sessionHistoryService.events(sessionId), config.orElse(null));
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
    return Single.just(ListEventsResponse.builder().events(sessionHistoryService.events(sessionId)).build());
  }

  private static List<Event> filterEvents(final List<Event> events, final GetSessionConfig config) {
    if (events == null || events.isEmpty())
      return List.of();
    final List<Event> result = new ArrayList<>(events);
    if (config == null)
      return result;
    if (config.numRecentEvents().isPresent()) {
      final int recentEventCount = config.numRecentEvents().get();
      return result.size() > recentEventCount ? result.subList(result.size() - recentEventCount, result.size()) : result;
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

  private static String resolveRootSessionId(final AgentSession parentSession) {
    if (parentSession == null || StringUtils.isBlank(parentSession.getRootSessionId())) {
      return parentSession == null ? null : parentSession.getId();
    }
    return parentSession.getRootSessionId();
  }

  private static String resolveRootAgentId(final AgentSession parentSession) {
    if (parentSession == null || StringUtils.isBlank(parentSession.getRootAgentId())) {
      return parentSession == null ? null : parentSession.getAgentId();
    }
    return parentSession.getRootAgentId();
  }
}
