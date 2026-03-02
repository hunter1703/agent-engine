package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.Violation;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
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
 * Detects redundant tool-call sequences.
 *
 * <p>Responsibilities:
 * - Compare current tool calls with the previous turn.
 * - Emit a violation on duplicate sequences (excluding submit_final_answer).
 * - Skip partial responses to avoid premature checks.
 *
 * <p>Ownership: tool-call redundancy checks.
 */
public final class RedundantToolCallsResponseProcessor implements ResponseProcessor {
  public static final RedundantToolCallsResponseProcessor INSTANCE = new RedundantToolCallsResponseProcessor();
  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final Content content = response.content().orElse(null);
    if (content == null) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final List<FunctionCall> toolCalls = ToolUtils.extractToolCalls(response);

    if (CollectionUtils.isEmpty(toolCalls)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final String lastTools = RunStateUtils.getState(context).lastToolCall();
    final List<FunctionCall> redundantCalls = toolCalls.stream()
        .filter(call -> lastTools != null && lastTools.contains(summarizeCall(call)))
        .toList();

    if (CollectionUtils.isNotEmpty(redundantCalls)) {
      return buildViolationResponse(context, response, redundantCalls);
    }

    // Record last tool call for future redundancy checks
    RunStateUtils.getState(context).updateLastToolCall(summarizeToolCalls(toolCalls));
    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }

  private String summarizeCall(final FunctionCall call) {
    return call.name().orElse("") + call.args().orElse(Map.of());
  }

  private String summarizeToolCalls(final List<FunctionCall> toolCalls) {
    return toolCalls.stream()
        .map(this::summarizeCall)
        .collect(Collectors.joining("|"));
  }

  private Single<ResponseProcessingResult> buildViolationResponse(
      final InvocationContext context, final LlmResponse response, final List<FunctionCall> redundant) {
    final String names = redundant.stream().map(c -> c.name().orElse("unknown")).collect(Collectors.joining(", "));
    final String msg = String.format("Redundancy Detected: You are repeating tool calls [%s] with the exact same arguments as the previous turn. Please adjust your strategy or update the arguments.", names);
    
    RunStateUtils.getState(context).addViolation(Violation.builder("redundant_tool_calls")
        .message("Redundant tool calls detected")
        .correctionMessage(msg)
        .build());

    final List<Part> filteredParts = response.content().flatMap(Content::parts).orElse(List.of()).stream()
        .filter(part -> part.functionCall().map(call -> !redundant.contains(call)).orElse(true))
        .toList();

    final LlmResponse updatedResponse = response.toBuilder()
        .content(response.content().get().toBuilder().parts(filteredParts).build())
        .turnComplete(false)
        .build();

    return Single.just(ResponseProcessingResult.create(
        updatedResponse,
        List.of(),
        Optional.empty()));
  }

}
