package com.agentengine.engine.sessions;

import com.agentengine.engine.api.utils.StringUtils;
import com.agentengine.engine.repository.AbstractMongoRepository;
import com.agentengine.engine.utils.Query;
import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import io.quarkus.mongodb.runtime.MongoClientSupport;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MongoSessionService extends AbstractMongoRepository<SessionInfo> implements BaseSessionService {
  private static final Logger LOG = LoggerFactory.getLogger(MongoSessionService.class);

  public MongoSessionService(final MongoClientSupport clientSupport) {
    super(clientSupport, "Session", SessionInfo.class);
  }

  @Override
  public Single<Session> createSession(final String appName, final String userId,
      final ConcurrentMap<String, Object> state, final String sessionId) {
    final String resolvedSessionId = StringUtils.isBlank(sessionId) ? UUID.randomUUID().toString() : sessionId;
    final ConcurrentMap<String, Object> initialState =
        state == null ? new ConcurrentHashMap<>() : new ConcurrentHashMap<>(state);
    final Session session = Session.builder(resolvedSessionId)
        .appName(appName)
        .userId(userId)
        .state(initialState)
        .events(new ArrayList<>())
        .lastUpdateTime(Instant.now())
        .build();
    save(new SessionInfo(session));
    return Single.just(session);
  }

  @Override
  public Maybe<Session> getSession(final String appName, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    final SessionInfo sessionInfo = findById(sessionId).orElse(null);
    if (sessionInfo == null) {
      return Maybe.empty();
    }
    final Session session = sessionInfo.toSession();
    final List<Event> events = filterEvents(session.events(), config.orElse(null));
    final Session.Builder builder = Session.builder(session.id())
        .appName(session.appName())
        .userId(session.userId())
        .state(session.state())
        .events(events)
        .lastUpdateTime(session.lastUpdateTime());
    return Maybe.just(builder.build());
  }

  @Override
  public Single<ListSessionsResponse> listSessions(final String appName, final String userId) {
    final List<Session> sessions = new ArrayList<>();
    findByQuery(new Query().withFilter(Filters.and(Filters.eq("appName", appName), Filters.eq("userId", userId))))
        .getItems().forEach(sessionInfo -> {
          final Session stored = sessionInfo.toSession();
          sessions.add(Session.builder(stored.id())
                  .appName(stored.appName())
                  .userId(stored.userId())
                  .lastUpdateTime(stored.lastUpdateTime())
                  .build());
        });
    return Single.just(ListSessionsResponse.builder().sessions(sessions).build());
  }

  @Override
  public Completable deleteSession(final String appName, final String userId, final String sessionId) {
    final boolean deleted = deleteById(sessionId);
    if (!deleted) {
      LOG.debug("Session delete did not match any document - session_id={}", sessionId);
    }
    return Completable.complete();
  }

  @Override
  public Single<ListEventsResponse> listEvents(final String appName, final String userId, final String sessionId) {
    final SessionInfo sessionInfo = findById(sessionId).orElse(null);
    if (sessionInfo == null) {
      return Single.just(ListEventsResponse.builder().build());
    }
    return Single.just(ListEventsResponse.builder().events(sessionInfo.toSession().events()).build());
  }

  @Override
  public Single<Event> appendEvent(final Session session, final Event event) {
    return BaseSessionService.super.appendEvent(session, event)
        .doOnSuccess(_ -> {
          if (!event.partial().orElse(false)) {
            session.lastUpdateTime(Instant.ofEpochMilli(event.timestamp()));
            save(new SessionInfo(session));
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
      return result.stream()
          .filter(event -> !Instant.ofEpochMilli(event.timestamp()).isBefore(threshold))
          .collect(Collectors.toList());
    }
    return result;
  }
}
