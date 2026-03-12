package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.FinishReason;
import io.reactivex.rxjava3.core.Single;

/**
 * Enforces turn-completion semantics on every non-partial {@code LlmResponse}.
 *
 * <p>Runs last in the response-processor chain (after all processors that may request
 * continuation). Responsibilities:
 *
 * <ul>
 *   <li>{@code turnComplete=true} on every non-partial response — each LlmResponse is the end of
 *       a model generation turn regardless of whether it carries a final answer or tool calls.
 *   <li>Consumes the continuation flag on every non-partial response to prevent cross-turn
 *       leakage.
 *   <li>On final-answer responses: if continuation was NOT
 *       requested, sets {@code finishReason=STOP} (preserving any existing value). If continuation
 *       WAS requested, omits {@code finishReason} — {@code BaseFlow} treats absence of
 *       {@code finishReason} on a terminal event as the signal to loop again.
 * </ul>
 */
public final class TurnCompletionResponseProcessor implements ResponseProcessor {
  public static final TurnCompletionResponseProcessor INSTANCE =
      new TurnCompletionResponseProcessor();

  private TurnCompletionResponseProcessor() {}

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return ResponseUtils.single(response.toBuilder().turnComplete(false).build());
    }
    final boolean continuationRequested = RunStateUtils.getState(context).consumeContinuation();
    final LlmResponse.Builder builder = response.toBuilder().turnComplete(true);
    if (ResponseUtils.isFinalAnswer(response)) {
      if (!continuationRequested) {
        builder.finishReason(response.finishReason().orElse(new FinishReason(FinishReason.Known.STOP)));
      }
    }
    return ResponseUtils.single(builder.build());
  }
}
