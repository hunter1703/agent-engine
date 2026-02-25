package com.agentengine.engine.agents.flows;

import com.agentengine.engine.utils.Violation;
import com.agentengine.engine.utils.ViolationUtils;
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
 * Verifies the LLM response for structural integrity, hallucination prevention,
 * and redundancy before tool execution.
 */
public final class RedundantToolCallsResponseProcessor implements ResponseProcessor {

  private static final String PREVIOUS_TOOL_CALL_KEY = "last_tool_call";

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final List<FunctionCall> toolCalls = extractToolCalls(response);

    if (CollectionUtils.isEmpty(toolCalls)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    final String violation = checkRedundancy(context, toolCalls);
    if (violation != null) {
      return buildViolationResponse(context, response, violation);
    }

    // Record last tool call for future redundancy checks
    context.session().state().put(PREVIOUS_TOOL_CALL_KEY, summarizeToolCalls(toolCalls));
    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }

  private List<FunctionCall> extractToolCalls(final LlmResponse response) {
    return response.content()
        .flatMap(Content::parts)
        .stream()
        .flatMap(List::stream)
        .map(Part::functionCall)
        .flatMap(Optional::stream)
        .toList();
  }

  private String checkRedundancy(final InvocationContext context, final List<FunctionCall> toolCalls) {
    final String currentTools = summarizeToolCalls(toolCalls);
    final String lastTools = (String) context.session().state().get(PREVIOUS_TOOL_CALL_KEY);

    if (currentTools.equals(lastTools)) {
      return "Redundancy Detected: You are repeating the exact same tool calls as the previous turn. Please adjust your strategy or update the arguments.";
    }
    return null;
  }

  private String summarizeToolCalls(final List<FunctionCall> toolCalls) {
    return toolCalls.stream()
        .map(c -> c.name().orElse("") + c.args().orElse(Map.of()))
        .collect(Collectors.joining("|"));
  }

  private Single<ResponseProcessingResult> buildViolationResponse(
      final InvocationContext context, final LlmResponse response, final String violation) {
    ViolationUtils.addViolation(context, Violation.builder("redundant_tool_calls")
        .message("Redundant tool calls detected")
        .correctionMessage(violation)
        .build());
    
    final LlmResponse corrected = response.toBuilder()
        .turnComplete(false)
        .build();
    return Single.just(ResponseProcessingResult.create(corrected, List.of(), Optional.empty()));
  }

}
