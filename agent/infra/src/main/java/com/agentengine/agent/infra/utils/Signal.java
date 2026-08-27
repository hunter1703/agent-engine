package com.agentengine.agent.infra.utils;

import java.util.Objects;

public record Signal<T>(String id, T context, boolean requiresContinuation) {

  @Override
  public boolean equals(Object o) {
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Signal<?> signal = (Signal<?>) o;
    return Objects.equals(id, signal.id);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(id);
  }
}
