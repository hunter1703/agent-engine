package com.agentengine.engine.agents.flows;

import com.agentengine.engine.api.utils.CollectionUtils;
import com.agentengine.engine.tools.planning.PlanningUtils;
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
    String violation = checkRedundancy(context, toolCalls);

      if (violation != null) {
      // Mark for retry with nudge
      PlanningUtils.setNudgeRequired(context, true);
      context.session().state().put(PlanningUtils.VIOLATION_MESSAGE_KEY, violation);
      
      final LlmResponse corrected = response.toBuilder()
          .partial(true)
          .turnComplete(false)
          .build();
          
      return Single.just(ResponseProcessingResult.create(corrected, List.of(), Optional.empty()));
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

}
