package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Optional;

/**
 * Handles final answer extraction and thought modes.
 *
 * <p>Responsibilities:
 * - Detect simultaneous text and final_answer tool calls.
 * - Strip text into thoughts when tool-calling is active.
 * - Detect missing final answer tool calls on concluding turns.
 *
 * <p>Ownership: final answer protocol enforcement.
 */
public final class FinalAnswerResponseProcessor implements ResponseProcessor {
  public static final FinalAnswerResponseProcessor INSTANCE = new FinalAnswerResponseProcessor();

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, LlmResponse response) {
    if (response.partial().orElse(false)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final Content content = response.content().orElse(null);
    if (content == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final RunState state = RunStateUtils.getState(context);
    final List<Part> parts = content.parts().orElse(List.of());
    final boolean hasText = parts.stream().anyMatch(part -> part.text().isPresent());
    final boolean hasNonThoughtText = parts.stream().anyMatch(part -> part.text().isPresent() && !part.thought().orElse(false));
    final boolean callsFinalAnswer = parts.stream().anyMatch(ToolUtils::callsFinalAnswerTool);
    final boolean hasExecutionTools = parts.stream().anyMatch(part -> part.functionCall().isPresent() && !ToolUtils.callsFinalAnswerTool(part));

    // 1. Simultaneous text and final answer (Violation)
    if (hasText && callsFinalAnswer) {
      final String msg = "Simultaneous text and final answer detected. Please move concluding text to the final answer tool arguments or thoughts.";
      state.addViolation(
              Violation.builder("final_answer_protocol")
                  .message("Simultaneous text and final answer tool call")
                  .correctionMessage(msg)
                  .build());

      return Single.just(ResponseProcessingResult.create(
          markIncomplete(sanitize(response)), 
          List.of(),
          Optional.empty()));
    }

    // 2. Protocol and Phase Transitions
    if (callsFinalAnswer) {
      if (state.phase() == RunState.Phase.FINAL_ANSWER_DELIVERED || state.phase() == RunState.Phase.FINISHED) {
        state.addViolation(Violation.builder("final_answer_already_complete")
            .message("Final answer signal ignored after completion.")
            .correctionMessage("The run is already complete.")
            .build());
      } else if (state.phase() == RunState.Phase.READY_FOR_FINAL_ANSWER || state.phase() == RunState.Phase.FINAL_ANSWER_REQUESTED) {
        state.addViolation(Violation.builder("final_answer_repeat_signal")
            .message("Final answer signal repeated.")
            .correctionMessage("Wait for the prompt.")
            .build());
      } else {
        state.transitionPhase(RunState.Phase.READY_FOR_FINAL_ANSWER);
      }
    }
    
    // 3. Auto-transition to Delivered if in Requested phase
    if (hasNonThoughtText && state.phase() == RunState.Phase.FINAL_ANSWER_REQUESTED) {
      state.transitionPhase(RunState.Phase.FINAL_ANSWER_DELIVERED);
    }

    // 4. Premature Termination check
    if (!callsFinalAnswer && !hasExecutionTools) {
       if (state.phase() == RunState.Phase.REASONING || state.phase() == RunState.Phase.READY_FOR_FINAL_ANSWER) {
          final String msg = "You provided a response without calling tools or the final answer. If you are finished, you MUST call submit_final_answer.";
          state.addViolation(Violation.builder("premature_termination")
              .message("Missing final answer signal")
              .correctionMessage(msg)
              .build());
          response = markIncomplete(sanitize(response));
       }
    }

    // 5. Thought stripping logic
    if (callsFinalAnswer || hasExecutionTools) {
      response = sanitize(response);
    }

    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }

  private static LlmResponse markIncomplete(final LlmResponse response) {
    return response.toBuilder().turnComplete(false).build();
  }

  private static LlmResponse sanitize(final LlmResponse response) {
    final Content content = response.content().orElse(Content.builder().build());
    final List<Part> remainingParts =
        content.parts().orElse(List.of()).stream()
            .filter(part -> !ToolUtils.callsFinalAnswerTool(part))
            .toList();

    final List<Part> thoughtOnly =
        remainingParts.stream()
            .map(part ->
                part.text().isPresent() && !part.thought().orElse(false)
                    ? part.toBuilder().thought(true).build()
                    : part)
            .toList();
    return response.toBuilder()
        .content(content.toBuilder().parts(thoughtOnly).build())
        .build();
  }
}
