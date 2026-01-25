package com.agentengine.engine.state;

import com.agentengine.engine.api.SessionStore;
import com.agentengine.engine.api.beans.session.Session;
import com.agentengine.engine.api.utils.StringUtils;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySessionStore implements SessionStore {
  private final Map<String, Session> sessions = new ConcurrentHashMap<>();

  @Override
  public Session findById(final String id) {
    return sessions.get(id);
  }

  @Override
  public Session createOrUpdate(final Session session) {
    String id = session.getId();
    if (StringUtils.isBlank(id)) {
      id = UUID.randomUUID().toString();
      session.setId(id);
    }
    sessions.put(id, session);
    return session;
  }
}