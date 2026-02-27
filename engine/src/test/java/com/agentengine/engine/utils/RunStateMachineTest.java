package com.agentengine.engine.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.agentengine.engine.api.utils.TransitionResult;
import org.junit.jupiter.api.Test;

class RunStateMachineTest {

  @Test
  void transitionsToReadyForFinalAnswer() {
    final RunStateMachine machine = new RunStateMachine(RunState.Phase.REASONING);

    final TransitionResult<Violation> result =
        machine.next(RunState.Phase.READY_FOR_FINAL_ANSWER);

    assertThat(result.success()).isTrue();
    assertThat(machine.getState()).isEqualTo(RunState.Phase.READY_FOR_FINAL_ANSWER);
  }

  @Test
  void rejectsReasoningAfterFinalAnswerReady() {
    final RunStateMachine machine =
        new RunStateMachine(RunState.Phase.READY_FOR_FINAL_ANSWER);

    final TransitionResult<Violation> result =
        machine.next(RunState.Phase.REASONING);

    assertThat(result.success()).isFalse();
    assertThat(result.value()).isNotNull();
    assertThat(result.value().getCode()).isEqualTo("invalid_phase_transition");
    assertThat(result.value().getMessage())
        .contains("Continued reasoning after signaling readiness");
  }
}
