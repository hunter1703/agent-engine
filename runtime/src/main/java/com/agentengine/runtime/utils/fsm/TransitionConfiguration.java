package com.agentengine.runtime.utils.fsm;

import com.github.oxo42.stateless4j.StateConfiguration;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public final class TransitionConfiguration<S, T, V> extends StateConfiguration<S, T> {
  private final Map<T, Supplier<V>> invalidHandlers = new HashMap<>();

  public TransitionConfiguration(final StateMachineConfiguration<S, T, V> parent, final S state) {
    super(parent.getRepresentation(state), parent::getRepresentation);
  }

  @Override
  public TransitionConfiguration<S, T, V> permit(final T trigger, final S destinationState) {
    super.permit(trigger, destinationState);
    return this;
  }

  public TransitionConfiguration<S, T, V> notPermit(final T trigger, final Supplier<V> handler) {
    invalidHandlers.put(trigger, handler);
    return this;
  }

  public Supplier<V> getHandler(final T trigger) {
    return invalidHandlers.get(trigger);
  }
}
