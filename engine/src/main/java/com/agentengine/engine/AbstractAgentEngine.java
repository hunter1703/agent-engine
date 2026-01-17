package com.agentengine.engine;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

public abstract class AbstractAgentEngine implements AgentEngine {

  protected ConcurrentMap<String, AgentListener> listeners = new ConcurrentHashMap<>();

  @Override
  public void registerListener(final String sessionId, final AgentListener listener) {
    listeners.putIfAbsent(sessionId, listener);
  }

  @Override
  public void unRegisterListener(final String sessionId) {
    listeners.remove(sessionId);
  }

  protected void invokeListeners(final Consumer<AgentListener> callback) {
    for (final AgentListener listener : listeners.values()) {
      callback.accept(listener);
    }
  }
}
