package com.agentengine.runtime.utils.fsm;

import java.util.Objects;
import java.util.function.Supplier;

public class StateMachine<S, T, V> extends com.github.oxo42.stateless4j.StateMachine<S, T> {
    private final StateMachineConfiguration<S, T, V> stateConfiguration;

    public StateMachine(final S initState, final StateMachineConfiguration<S, T, V> stateConfiguration) {
        super(initState, stateConfiguration);
        this.stateConfiguration = stateConfiguration;
    }

    public TransitionResult<V> next(final T trigger) {
        if (!canFire(trigger)) {
            final Supplier<V> handler = stateConfiguration.getHandler(getState(), trigger);
            final V value = handler == null ? null : handler.get();
            return TransitionResult.failure(value);
        }
        fire(trigger);
        return TransitionResult.successful();
    }

    public TransitionResult<V> tryFire(final S expectedState, final T trigger) {
        if (!Objects.equals(getState(), expectedState)) {
            return TransitionResult.failure(null);
        }
        return next(trigger);
    }
}
