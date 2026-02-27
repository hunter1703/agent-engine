package com.agentengine.engine.utils;

import com.agentengine.engine.api.utils.StateMachine;
import com.agentengine.engine.api.utils.StateMachineConfiguration;
import com.agentengine.engine.api.utils.TransitionConfiguration;
import com.agentengine.engine.utils.RunState.Phase;

import java.util.List;

import static com.agentengine.engine.utils.RunState.Phase.*;

public final class RunStateMachine
    extends StateMachine<Phase, Phase, Violation> {

  public RunStateMachine(final Phase initialState) {
    super(initialState, buildConfig());
  }

  private static StateMachineConfiguration<Phase, Phase, Violation> buildConfig() {
    final StateMachineConfiguration<Phase, Phase, Violation> configuration = new StateMachineConfiguration<>();
    configureTransitions(configuration, REASONING, List.of(REASONING, READY_FOR_FINAL_ANSWER));
    configureTransitions(configuration, READY_FOR_FINAL_ANSWER, List.of(FINAL_ANSWER_REQUESTED));
    configureTransitions(configuration, FINAL_ANSWER_REQUESTED, List.of(FINAL_ANSWER_DELIVERED));
    configureTransitions(configuration, UNKNOWN, List.of(REASONING));
    configureTransitions(configuration, FINAL_ANSWER_DELIVERED, List.of(FINISHED));
    configureTransitions(configuration, FINISHED, List.of());
    return configuration;
  }

  private static void configureTransitions(final StateMachineConfiguration<Phase, Phase, Violation> configuration, final Phase fromState, final List<Phase> validTargets) {
    final TransitionConfiguration<Phase, Phase, Violation> config = configuration.configure(fromState);
    for (final Phase target : values()) {
      if (validTargets.contains(target)) {
        config.permit(target, target);
      } else {
        config.notPermit(target, () -> invalidTransitionViolation(fromState, target));
      }
    }
  }

  private static Violation invalidTransitionViolation(
      final Phase fromState, final Phase toState) {
    final String reason = switch (fromState) {
      case REASONING -> switch (toState) {
        case FINAL_ANSWER_REQUESTED ->
            "Final answer requested before calling submit_final_answer.";
        case FINAL_ANSWER_DELIVERED ->
            "Final answer delivered without being requested.";
        case UNKNOWN ->
            "Cannot transition to UNKNOWN while reasoning.";
        case FINISHED ->
            null;
        case READY_FOR_FINAL_ANSWER, REASONING ->
            "Invalid transition from REASONING to " + toState + ".";
      };
      case READY_FOR_FINAL_ANSWER -> switch (toState) {
        case REASONING ->
            "Continued reasoning after signaling readiness for the final answer.";
        case FINAL_ANSWER_DELIVERED ->
            "Final answer delivered before the final answer prompt.";
        case UNKNOWN ->
            "Cannot transition to UNKNOWN after signaling readiness.";
        case READY_FOR_FINAL_ANSWER, FINAL_ANSWER_REQUESTED ->
            "Invalid transition from READY_FOR_FINAL_ANSWER to " + toState + ".";
        case FINISHED ->
            null;
      };
      case FINAL_ANSWER_REQUESTED -> switch (toState) {
        case REASONING ->
            "Continued reasoning after the final answer was requested.";
        case READY_FOR_FINAL_ANSWER ->
            "Repeated final answer signal after the final answer prompt.";
        case UNKNOWN ->
            "Cannot transition to UNKNOWN while the final answer is required.";
        case FINAL_ANSWER_REQUESTED, FINAL_ANSWER_DELIVERED ->
            "Invalid transition from FINAL_ANSWER_REQUESTED to " + toState + ".";
        case FINISHED ->
            null;
      };
      case FINAL_ANSWER_DELIVERED ->
          "Output emitted after the final answer was delivered (" + toState + ").";
      case FINISHED ->
          "Output emitted after the run was finished (" + toState + ").";
      case UNKNOWN ->
          "Run state is UNKNOWN; cannot transition to " + toState + ".";
    };
    final String guidance = switch (fromState) {
      case REASONING ->
          "Continue reasoning or call tools. Call submit_final_answer when you are ready to answer.";
      case READY_FOR_FINAL_ANSWER ->
          "Wait for the final answer prompt, then provide only the final answer text.";
      case FINAL_ANSWER_REQUESTED ->
          "Provide the final answer text now. Do not call tools or return to reasoning.";
      case FINAL_ANSWER_DELIVERED ->
          "The final answer has already been delivered. Do not emit any more output.";
      case FINISHED ->
          "The run has already finished. Do not emit any more output.";
      case UNKNOWN ->
          "Begin reasoning from scratch and proceed normally.";
    };
    final String message = "Invalid phase transition from " + fromState + " to " + toState + ".";
    final String correctionMessage = reason == null ? guidance : reason + " " + guidance;
    return Violation.builder("invalid_phase_transition")
        .message(message)
        .correctionMessage(correctionMessage)
        .build();
  }
}
