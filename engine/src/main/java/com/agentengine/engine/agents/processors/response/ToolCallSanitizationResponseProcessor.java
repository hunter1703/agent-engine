package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.RunState.ToolCallSignature;
import com.agentengine.engine.utils.RunUtils;
import com.agentengine.engine.utils.Violation;
import com.agentengine.util.common.CollectionUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.Content;
import com.google.genai.types.FunctionCall;
import com.google.genai.types.Part;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Sanitizes tool calls in model responses, enforcing protocol constraints and
 * detecting logical errors.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 * <li><b>Partial responses:</b> No tool call or response parts appear in
 * partial responses. (If present, they are stripped.)
 * <li><b>Redundant tool calls:</b> Tool calls that are identical (same name and
 * arguments) to the previous turn are removed from the response.
 * <li><b>Invalid tool calls:</b> If all tool calls in a full response are
 * invalid (stripped), the response is passed back through the loop for
 * regeneration (continuation is signaled).
 * </ul>
 */
public final class ToolCallSanitizationResponseProcessor implements ResponseProcessor {
  public static final ToolCallSanitizationResponseProcessor INSTANCE = new ToolCallSanitizationResponseProcessor();

  private ToolCallSanitizationResponseProcessor() {
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    return response.partial().orElse(false) ? handlePartial(context, response) : handleFull(context, response);
  }

  // ── Partial ──────────────────────────────────────────────────────────────

  private static Single<ResponseProcessingResult> handlePartial(final InvocationContext context, final LlmResponse response) {
    final Content content = response.content().orElse(null);
    if (content == null) {
      return ResponseUtils.single(response);
    }
    final List<Part> parts = content.parts().orElse(List.of());
    final List<Part> toolParts = parts.stream().filter(p -> p.functionCall().isPresent() || p.functionResponse().isPresent()).toList();
    if (toolParts.isEmpty()) {
      return ResponseUtils.single(response);
    }

    final String toolSummary = ToolUtils.summarizeToolParts(toolParts);
    final String correctionMessage = toolSummary.isBlank()
        ? "Tool calls and responses are not allowed in partial responses." + " Emit them only in non-partial turns."
        : "Tool calls and responses are not allowed in partial responses." + " Emit them only in non-partial turns."
            + " Following tool parts have been stripped: " + toolSummary + ".";
    RunUtils.getOrInitState(context).addViolation(
        Violation.builder("partial_tool_calls").message("Tool calls in partial response").correctionMessage(correctionMessage).build());

    final List<Part> textOnly = parts.stream().filter(p -> p.functionCall().isEmpty() && p.functionResponse().isEmpty()).toList();
    return ResponseUtils.single(response.toBuilder().content(content.toBuilder().parts(textOnly).build()).build());
  }

  // ── Full ─────────────────────────────────────────────────────────────────

  private static Single<ResponseProcessingResult> handleFull(final InvocationContext context, final LlmResponse response) {
    if (response.content().isEmpty()) {
      return ResponseUtils.single(response);
    }
    final List<FunctionCall> toolCalls = ToolUtils.extractToolCalls(response);
    if (CollectionUtils.isEmpty(toolCalls)) {
      return ResponseUtils.single(response);
    }

    final Set<ToolCallSignature> previousCalls = Set.copyOf(RunUtils.getOrInitState(context).lastToolCalls());
    final List<FunctionCall> redundant = toolCalls.stream().filter(call -> previousCalls.contains(toSignature(call))).toList();

    if (CollectionUtils.isNotEmpty(redundant)) {
      return buildRedundancyResponse(context, response, redundant);
    }

    final List<ToolCallSignature> currentCalls = toolCalls.stream().map(ToolCallSanitizationResponseProcessor::toSignature).toList();
    RunUtils.getOrInitState(context).updateLastToolCalls(currentCalls);
    return ResponseUtils.single(response);
  }

  private static Single<ResponseProcessingResult> buildRedundancyResponse(final InvocationContext context, final LlmResponse response,
      final List<FunctionCall> redundant) {
    final String names = redundant.stream().map(c -> c.name().orElse("unknown")).collect(Collectors.joining(", "));
    final String msg = String.format("Redundancy Detected: You are repeating tool calls [%s] with the exact same arguments"
        + " as the previous turn. Please adjust your strategy or update the arguments.", names);
    RunUtils.getOrInitState(context)
        .addViolation(Violation.builder("redundant_tool_calls").message("Redundant tool calls detected").correctionMessage(msg).build());

    final List<Part> filtered = response.content().flatMap(Content::parts).orElse(List.of()).stream()
        .filter(p -> p.functionCall().map(call -> !redundant.contains(call)).orElse(true)).toList();
    final Content filteredContent = response.content().get().toBuilder().parts(filtered).build();
    final LlmResponse filteredResponse = response.toBuilder().content(filteredContent).build();
    final boolean hasRemainingToolParts = filtered.stream()
        .anyMatch(part -> part.functionCall().isPresent() || part.functionResponse().isPresent());
    if (hasRemainingToolParts) {
      return ResponseUtils.single(filteredResponse);
    }
    RunUtils.getOrInitState(context).requestContinuation();
    return ResponseUtils.single(filteredResponse);
  }

  // ── Helpers ───────────────────────────────────────────────────────────────

  private static ToolCallSignature toSignature(final FunctionCall call) {
    return new ToolCallSignature(call.name().orElse(null), call.args().orElse(Map.of()));
  }
}
