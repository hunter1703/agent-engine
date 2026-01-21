package com.agentengine.engine.api;

/**
 * Lifecycle listener for the engine.
 */
public interface EngineListener {
  default void onStart(String sessionId) {
  }

  default void onEnd(String sessionId) {
  }

  default void onError(String sessionId, Throwable t) {
  }
}
