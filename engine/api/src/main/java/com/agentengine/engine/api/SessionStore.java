package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Session;

public interface SessionStore {

  Session findById(String id);

  Session createOrUpdate(Session session);
}