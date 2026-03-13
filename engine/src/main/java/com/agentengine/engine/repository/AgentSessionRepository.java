package com.agentengine.engine.repository;

import com.agentengine.engine.api.beans.session.AgentSession;
import com.agentengine.engine.api.beans.session.SessionInfo;
import com.agentengine.util.common.StringUtils;
import com.agentengine.util.common.query.Filter;
import com.agentengine.util.common.query.Filters;
import com.agentengine.util.common.query.Query;
import com.agentengine.util.common.update.Operation;
import com.agentengine.util.common.update.Update;
import com.agentengine.util.common.validation.ValidationService;
import com.agentengine.util.mongodb.mongo.AbstractMongoRepository;
import com.agentengine.util.mongodb.mongo.MongoClientFactory;
import com.google.adk.events.Event;
import com.google.adk.flows.llmflows.Functions;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Repository for managing Session entities */
@Singleton
public class AgentSessionRepository extends AbstractMongoRepository<AgentSession> implements BaseSessionService {
  private static final Logger LOG = LoggerFactory.getLogger(AgentSessionRepository.class);

  @Inject
  public AgentSessionRepository(final MongoClientFactory mongoClientFactory, ValidationService validationService) {
    super(mongoClientFactory, "AgentSession", AgentSession.class, validationService);
  }

  @Override
  public Single<Session> createSession(final String agentId, final String userId, final ConcurrentMap<String, Object> state,
      final String sessionId) {
    final String resolvedSessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    final ConcurrentMap<String, Object> initialState = state == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(state);
    final Session session = Session.builder(resolvedSessionId).appName(agentId).userId(userId).state(initialState).events(new ArrayList<>())
        .lastUpdateTime(Instant.now()).build();

    final AgentSession agentSession = new AgentSession(resolvedSessionId, agentId, session);
    insert(agentSession);
    return Single.just(session);
  }

  @Override
  public Maybe<Session> getSession(final String agentId, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    final SessionInfo sessionInfo = findById(sessionId).map(AgentSession::getSessionInfo).orElse(null);
    if (sessionInfo == null) {
      return Maybe.empty();
    }
    final Session session = sessionInfo.toSession();
    final List<Event> events = filterEvents(session.events(), config.orElse(null));
    final Session.Builder builder = Session.builder(session.id()).appName(session.appName()).userId(session.userId()).state(session.state())
        // events need to be mutable so the runtime can append later events,
        // particularly when appending new events
        // (com.google.adk.sessions.BaseSessionService.appendEvent)
        .events(new ArrayList<>(events)).lastUpdateTime(session.lastUpdateTime());
    return Maybe.just(builder.build());
  }

  @Override
  public Single<ListSessionsResponse> listSessions(final String agentId, final String userId) {
    final List<Session> sessions = new ArrayList<>();

    final Filter filter = Filters.and(Filters.eq("agentId", agentId), Filters.eq("userId", userId));
    Query query = new Query().withFilter(filter);
    findByQuery(query).getItems().forEach(agentSession -> {
      final SessionInfo sessionInfo = agentSession.getSessionInfo();
      final Session stored = sessionInfo.toSession();
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
    final SessionInfo sessionInfo = findById(sessionId).map(AgentSession::getSessionInfo).orElse(null);
    if (sessionInfo == null) {
      return Single.just(ListEventsResponse.builder().build());
    }
    return Single.just(ListEventsResponse.builder().events(sessionInfo.toSession().events()).build());
  }

  @Override
  public Single<Event> appendEvent(final Session session, final Event event) {
    Functions.populateClientFunctionCallId(event);
    return BaseSessionService.super.appendEvent(session, event).doOnSuccess(_ -> {
      if (!event.partial().orElse(false)) {
        final Operation setSessionInfo = Operation.set(AgentSession.FIELD_SESSION_INFO, SessionInfo.fromSession(session));
        final Operation setLastUpdateTime = Operation.set(AgentSession.FIELD_UPDATED_TIME, System.currentTimeMillis());
        update(session.id(), Update.of(setSessionInfo, setLastUpdateTime));
      }
    });
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
}
