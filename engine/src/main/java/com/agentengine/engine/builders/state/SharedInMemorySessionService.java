package com.agentengine.engine.builders.state;

import com.google.adk.sessions.BaseSessionService;
import com.google.adk.sessions.GetSessionConfig;
import com.google.adk.sessions.InMemorySessionService;
import com.google.adk.sessions.ListEventsResponse;
import com.google.adk.sessions.ListSessionsResponse;
import com.google.adk.sessions.Session;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;

import java.util.Optional;
import java.util.concurrent.ConcurrentMap;

/**
 * A wrapper around InMemorySessionService that ensures all instances share the same underlying storage.
 * This is needed to ensure sessions created in one runtime are available in another.
 */
public class SharedInMemorySessionService implements BaseSessionService {
    private static final InMemorySessionService SHARED_INSTANCE = new InMemorySessionService();

    @Override
    public Single<Session> createSession(String appName, String userId, ConcurrentMap<String, Object> state, String sessionId) {
        return SHARED_INSTANCE.createSession(appName, userId, state, sessionId);
    }

    @Override
    public Maybe<Session> getSession(String appName, String userId, String sessionId, Optional<GetSessionConfig> config) {
        return SHARED_INSTANCE.getSession(appName, userId, sessionId, config);
    }

    @Override
    public Single<ListSessionsResponse> listSessions(String appName, String userId) {
        return SHARED_INSTANCE.listSessions(appName, userId);
    }

    @Override
    public Completable deleteSession(String appName, String userId, String sessionId) {
        return SHARED_INSTANCE.deleteSession(appName, userId, sessionId);
    }

    @Override
    public Single<ListEventsResponse> listEvents(String appName, String userId, String sessionId) {
        return SHARED_INSTANCE.listEvents(appName, userId, sessionId);
    }

    @Override
    public Completable closeSession(Session session) {
        return SHARED_INSTANCE.closeSession(session);
    }
}