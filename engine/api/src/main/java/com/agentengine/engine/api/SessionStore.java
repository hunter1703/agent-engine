package com.agentengine.engine.api;

import com.agentengine.engine.api.beans.session.Message;
import com.agentengine.engine.api.beans.session.Session;

import java.util.List;

public interface SessionStore {

  Session findById(String id);

  Session createOrUpdate(Session session);
}