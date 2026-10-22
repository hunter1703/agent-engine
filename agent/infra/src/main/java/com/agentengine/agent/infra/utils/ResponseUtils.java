package com.agentengine.agent.infra.utils;

import com.agentengine.agent.infra.tools.HumanInTheLoopTool;
import com.agentengine.util.agents.Constants;
import com.agentengine.util.agents.beans.InterruptKind;
import com.agentengine.util.common.utils.StringUtils;
import com.google.adk.events.ToolConfirmation;
import com.google.adk.flows.llmflows.Functions;
import com.google.adk.flows.llmflows.ResponseProcessor.ResponseProcessingResult;
import com.google.adk.events.Event;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.*;

/** Shared flow helpers for event termination and response continuation shaping. */
public final class ResponseUtils {

  private ResponseUtils() {}

  /**
   * Whether {@code response} is the model's answer: complete, with no tool call. It may be empty;
   * text written next to a tool call is narration, not the answer.
   */
  public static boolean isFinalAnswer(final LlmResponse response) {
    return response != null && !response.partial().orElse(false) && !hasToolCall(response);
  }

  /** The same test for the event a response became, which must be the model's own. */
  public static boolean isFinalAnswer(final Event event) {
    return EventUtils.isModelEvent(event)
        && !event.partial().orElse(false)
        && event.functionCalls().isEmpty()
        && event.functionResponses().isEmpty();
  }

  private static boolean hasToolCall(final LlmResponse response) {
    return response.content().map(content -> content.parts().orElse(List.<Part>of())).orElse(List.of()).stream()
        .anyMatch(part -> part.functionCall().isPresent() || part.functionResponse().isPresent());
  }

  public static Single<ResponseProcessingResult> single(final LlmResponse response) {
    return Single.just(ResponseProcessingResult.create(response, List.of()));
  }

  public static ToolConfirmation buildToolConfirmation(
      final Boolean confirmed, final Map<String, Object> answer) {
    if (confirmed == null && answer == null) {
      throw new IllegalArgumentException("either confirmation or answer is required");
    }
    final ToolConfirmation.Builder builder = ToolConfirmation.builder();
    if (answer != null) {
      builder.payload(answer);
    }
    return builder.confirmed(Objects.requireNonNullElse(confirmed, true)).build();
  }

  public static LlmResponse requestHumanToDecide(final String question) {
    final String message =
        StringUtils.isBlank(question) ? "User input is required to continue." : question.trim();
    final FunctionCall functionCall =
        FunctionCall.builder()
            .id(Functions.generateClientFunctionCallId())
            .name(Constants.ToolNames.HITL)
            .args(
                Map.of(
                    HumanInTheLoopTool.QUESTION,
                    message,
                    HumanInTheLoopTool.KIND,
                    InterruptKind.DECISION.name()))
            .build();
    final Content content =
        Content.builder()
            .role("model")
            .parts(List.of(Part.builder().functionCall(functionCall).build()))
            .build();
    return LlmResponse.builder().content(content).turnComplete(true).build();
  }
}
