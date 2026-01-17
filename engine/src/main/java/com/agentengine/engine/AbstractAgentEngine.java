package com.agentengine.engine;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public abstract class AbstractAgentEngine implements AgentEngine {

  protected List<AgentListener> listeners = new CopyOnWriteArrayList<>();

  public void registerListener(final AgentListener listener) {
    listeners.add(listener);
  }

  protected void invokeListeners(final Consumer<AgentListener> callback) {
    for (final AgentListener listener : listeners) {
      callback.accept(listener);
    }
  }
}
