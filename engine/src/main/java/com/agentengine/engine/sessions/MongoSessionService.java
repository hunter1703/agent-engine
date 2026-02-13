package com.agentengine.engine.sessions;

import com.agentengine.engine.api.utils.StringUtils;
import com.google.adk.JsonBaseModel;
import com.google.adk.events.Event;
import com.google.adk.events.EventActions;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import com.google.adk.sessions.State;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.mongodb.client.result.DeleteResult;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
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
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MongoSessionService implements BaseSessionService {
  private static final Logger LOG = LoggerFactory.getLogger(MongoSessionService.class);
  private final MongoCollection<Session> sessionCollection;

  public MongoSessionService(final MongoClient mongoClient, final String database, final String collection) {
    Objects.requireNonNull(mongoClient, "mongoClient cannot be null");
    this.sessionCollection = mongoClient.getDatabase(database).getCollection(collection, Session.class);
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
    persistSession(session);
    return Single.just(session);
  }

  @Override
  public Maybe<Session> getSession(final String appName, final String userId, final String sessionId,
      final Optional<GetSessionConfig> config) {
    final Session session = sessionCollection.find(Filters.eq("_id", sessionId), Session.class).first();
    if (session == null) {
      return Maybe.empty();
    }
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
    sessionCollection.find(Filters.and(Filters.eq("appName", appName), Filters.eq("userId", userId)), Session.class)
        .forEach(stored -> {
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
    final DeleteResult result = sessionCollection.deleteOne(Filters.eq("_id", sessionId));
    if (result.getDeletedCount() == 0) {
      LOG.debug("Session delete did not match any document - session_id={}", sessionId);
    }
    return Completable.complete();
  }

  @Override
  public Single<ListEventsResponse> listEvents(final String appName, final String userId, final String sessionId) {
    final Session session = sessionCollection.find(Filters.eq("_id", sessionId), Session.class).first();
    if (session == null) {
      return Single.just(ListEventsResponse.builder().build());
    }
    final List<Event> events = session.events();
    return Single.just(ListEventsResponse.builder().events(events).build());
  }

  @Override
  public Single<Event> appendEvent(final Session session, final Event event) {
    return BaseSessionService.super.appendEvent(session, event)
        .doOnSuccess(_ -> {
          if (!event.partial().orElse(false)) {
            session.lastUpdateTime(Instant.ofEpochMilli(event.timestamp()));
            persistSession(session);
          }
        });
  }

  private void persistSession(final Session session) {
    sessionCollection.replaceOne(Filters.eq("_id", session.id()), session, new ReplaceOptions().upsert(true));
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
