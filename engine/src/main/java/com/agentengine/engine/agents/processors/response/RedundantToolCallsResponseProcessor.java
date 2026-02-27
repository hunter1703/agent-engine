package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.SubmitFinalAnswerTool;
import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.Violation;
import com.agentengine.engine.utils.RunStateUtils;
import com.agentengine.engine.api.utils.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.FunctionCall;
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

    final List<FunctionCall> toolCalls = ToolUtils.extractToolCalls(response).stream()
        .filter(call -> !SubmitFinalAnswerTool.TOOL_NAME.equals(call.name().orElse(null)))
        .toList();

    if (CollectionUtils.isEmpty(toolCalls)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }
    final String violation = checkRedundancy(context, toolCalls);
    if (violation != null) {
      return buildViolationResponse(context, response, violation);
    }

    // Record last tool call for future redundancy checks
    RunStateUtils.getState(context).updateLastToolCall(summarizeToolCalls(toolCalls));
    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }

  private String checkRedundancy(final InvocationContext context, final List<FunctionCall> toolCalls) {
    final String currentTools = summarizeToolCalls(toolCalls);
    final String lastTools = RunStateUtils.getState(context).lastToolCall();

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
    RunStateUtils.getState(context).addViolation(Violation.builder("redundant_tool_calls")
        .message("Redundant tool calls detected")
        .correctionMessage(violation)
        .build());
    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }

}
