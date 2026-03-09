package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.utils.Violation;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Sanitizes tool calls in model responses.
 *
 * <p>Responsibilities:
 * - Partial responses: detect and strip tool call/response parts; emit a protocol violation so the
 *   model is corrected on its next turn.
 * - Full responses: detect and strip redundant tool-call sequences (same calls as the previous
 *   turn); emit a redundancy violation.
 *
 * <p>Ownership: tool-call protocol enforcement across partial and full responses.
 */
public final class ToolCallSanitizationResponseProcessor implements ResponseProcessor {
  public static final ToolCallSanitizationResponseProcessor INSTANCE =
      new ToolCallSanitizationResponseProcessor();

  private ToolCallSanitizationResponseProcessor() {}

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    return response.partial().orElse(false)
        ? handlePartial(context, response)
        : handleFull(context, response);
  }

  // ── Partial ──────────────────────────────────────────────────────────────

  private static Single<ResponseProcessingResult> handlePartial(
      final InvocationContext context, final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return pass(response);
    }
    final List<Part> parts = content.parts().orElse(List.of());
    final List<Part> toolParts =
        parts.stream()
            .filter(p -> p.functionCall().isPresent() || p.functionResponse().isPresent())
            .toList();
    if (toolParts.isEmpty()) {
      return pass(response);
    }

    final String toolSummary = ToolUtils.summarizeToolParts(toolParts);
    final String correctionMessage =
        toolSummary.isBlank()
            ? "Tool calls and responses are not allowed in partial responses."
                + " Emit them only in non-partial turns."
            : "Tool calls and responses are not allowed in partial responses."
                + " Emit them only in non-partial turns."
                + " Following tool parts have been stripped: "
                + toolSummary
                + ".";
    RunStateUtils.getState(context)
        .addViolation(
            Violation.builder("partial_tool_calls")
                .message("Tool calls in partial response")
                .correctionMessage(correctionMessage)
                .build());

    final List<Part> textOnly =
        parts.stream()
            .filter(p -> p.functionCall().isEmpty() && p.functionResponse().isEmpty())
            .toList();
    return pass(response.toBuilder().content(content.toBuilder().parts(textOnly).build()).build());
  }

  // ── Full ─────────────────────────────────────────────────────────────────

  private static Single<ResponseProcessingResult> handleFull(
      final InvocationContext context, final LlmResponse response) {
    if (response.content().isEmpty()) {
      return pass(response);
    }
    final List<FunctionCall> toolCalls = ToolUtils.extractToolCalls(response);
    if (CollectionUtils.isEmpty(toolCalls)) {
      return pass(response);
    }

    final String lastTools = RunStateUtils.getState(context).lastToolCall();
    final List<FunctionCall> redundant =
        toolCalls.stream()
            .filter(call -> lastTools != null && lastTools.contains(summarizeCall(call)))
            .toList();

    if (CollectionUtils.isNotEmpty(redundant)) {
      return buildRedundancyResponse(context, response, redundant);
    }

    RunStateUtils.getState(context).updateLastToolCall(summarizeCalls(toolCalls));
    return pass(response);
  }

  private static Single<ResponseProcessingResult> buildRedundancyResponse(
      final InvocationContext context,
      final LlmResponse response,
      final List<FunctionCall> redundant) {
    final String names =
        redundant.stream().map(c -> c.name().orElse("unknown")).collect(Collectors.joining(", "));
    final String msg =
        String.format(
            "Redundancy Detected: You are repeating tool calls [%s] with the exact same arguments"
                + " as the previous turn. Please adjust your strategy or update the arguments.",
            names);
    RunStateUtils.getState(context)
        .addViolation(
            Violation.builder("redundant_tool_calls")
                .message("Redundant tool calls detected")
                .correctionMessage(msg)
                .build());

    final List<Part> filtered =
        response.content().flatMap(Content::parts).orElse(List.of()).stream()
            .filter(p -> p.functionCall().map(call -> !redundant.contains(call)).orElse(true))
            .toList();
    return pass(
        response
            .toBuilder()
            .content(response.content().get().toBuilder().parts(filtered).build())
            .turnComplete(false)
            .build());
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static String summarizeCall(final FunctionCall call) {
    return call.name().orElse("") + call.args().orElse(Map.of());
  }

  private static String summarizeCalls(final List<FunctionCall> calls) {
    return calls.stream()
        .map(ToolCallSanitizationResponseProcessor::summarizeCall)
        .collect(Collectors.joining("|"));
  }

  private static Single<ResponseProcessingResult> pass(final LlmResponse response) {
    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }
}
