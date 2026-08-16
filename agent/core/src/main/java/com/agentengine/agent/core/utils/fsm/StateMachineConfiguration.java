package com.agentengine.agent.core.utils.fsm;

import com.github.oxo42.stateless4j.StateMachineConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Supplier;

public class StateMachineConfiguration<S, T, V> extends StateMachineConfig<S, T> {
  private final Map<S, TransitionConfiguration<S, T, V>> configurations = new HashMap<>();

  @Override
  public TransitionConfiguration<S, T, V> configure(final S state) {
    return configurations.computeIfAbsent(
        state,
        value -> {
          super.configure(value);
          return new TransitionConfiguration<>(this, value);
        });
  }

  public Supplier<V> getHandler(final S from, final T trigger) {
    final TransitionConfiguration<S, T, V> config = configurations.get(from);
    return config == null ? null : config.getHandler(trigger);
  }
}
