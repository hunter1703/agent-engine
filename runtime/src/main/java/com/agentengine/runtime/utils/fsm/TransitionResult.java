package com.agentengine.runtime.utils.fsm;

public record TransitionResult<V>(boolean success, V value) {

  public static <V> TransitionResult<V> failure(final V value) {
    return new TransitionResult<>(false, value);
  }

  public static <V> TransitionResult<V> successful() {
    return new TransitionResult<>(true, null);
  }
}
