package com.agentengine.engine.api.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.utils.StringUtils;
import org.junit.jupiter.api.Test;

class StateMachineTest {

  @Test
  void transitionsReturnSuccessOrFailure() {
    final StateMachineConfiguration<LifecycleState, LifecycleState, String> config =
        new StateMachineConfiguration<>();
    config.configure(LifecycleState.IDLE)
        .permit(LifecycleState.RUNNING, LifecycleState.RUNNING);
    config.configure(LifecycleState.RUNNING)
        .permit(LifecycleState.COMPLETED, LifecycleState.COMPLETED)
        .notPermit(LifecycleState.IDLE, () -> "nope");

    final StateMachine<LifecycleState, LifecycleState, String> stateMachine =
        new StateMachine<>(LifecycleState.IDLE, config);

    final TransitionResult<String> startResult = stateMachine.next(LifecycleState.RUNNING);

    assertThat(startResult.success()).isTrue();
    assertThat(stateMachine.getState()).isEqualTo(LifecycleState.RUNNING);

    final TransitionResult<String> invalidResult = stateMachine.next(LifecycleState.IDLE);

    assertThat(invalidResult.success()).isFalse();
    assertThat(invalidResult.value()).isEqualTo("nope");
  }

  private enum LifecycleState {
    UNKNOWN,
    IDLE,
    RUNNING,
    COMPLETED;

    public static LifecycleState valueOfOrDefault(final String value) {
      if (StringUtils.isBlank(value)) {
        return UNKNOWN;
      }
      try {
        return LifecycleState.valueOf(value);
      } catch (IllegalArgumentException ex) {
        return UNKNOWN;
      }
    }
  }
}
