package com.agentengine.agent.infra.session;

import com.google.adk.events.Event;
import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

/** A session service that hands every call to {@code delegate}; subclasses override what differs. */
public class DelegatingSessionService implements BaseSessionService {
  private final BaseSessionService delegate;

  public DelegatingSessionService(final BaseSessionService delegate) {
    this.delegate = delegate;
  }

  @Override
  @SuppressWarnings("deprecation")
  public Single<Session> createSession(
      final String appName,
      final String userId,
      final ConcurrentMap<String, Object> state,
      final String sessionId) {
    return delegate.createSession(appName, userId, state, sessionId);
  }

  @Override
  public Maybe<Session> getSession(
      final String appName,
      final String userId,
      final String sessionId,
      final Optional<GetSessionConfig> config) {
    return delegate.getSession(appName, userId, sessionId, config);
  }

  @Override
  public Single<ListSessionsResponse> listSessions(final String appName, final String userId) {
    return delegate.listSessions(appName, userId);
  }

  @Override
  public Completable deleteSession(
      final String appName, final String userId, final String sessionId) {
    return delegate.deleteSession(appName, userId, sessionId);
  }

  @Override
  public Single<ListEventsResponse> listEvents(
      final String appName, final String userId, final String sessionId) {
    return delegate.listEvents(appName, userId, sessionId);
  }

  @Override
  public Completable closeSession(final Session session) {
    return delegate.closeSession(session);
  }

  @Override
  public Single<Event> appendEvent(final Session session, final Event event) {
    return delegate.appendEvent(session, event);
  }
}
