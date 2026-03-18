package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.utils.ResponseUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.FinishReason;
import io.reactivex.rxjava3.core.Single;

/**
 * Enforces run-completion semantics on every non-partial {@code LlmResponse}.
 *
 * <p>
 * Runs last in the response-processor chain (after all processors that may
 * request continuation). Owns termination signaling for LLM-generated responses
 * — the only place that can act on LLM output to set {@code finishReason}
 * appropriately.
 *
 * <h3>Guarantees</h3>
 *
 * <ul>
 * <li><b>Partial vs. final response handling:</b> Partial responses (streaming
 * deltas) receive {@code turnComplete=false}. Non-partial responses (final
 * assembly) receive {@code turnComplete=true}.
 * <li><b>Termination on final answers:</b>
 * <ul>
 * <li>If no continuation is requested: response has {@code finishReason=STOP}.
 * <li>If continuation is requested: response has no {@code finishReason} (loop
 * continues).
 * </ul>
 * <li><b>Tool call handling:</b> Tool-call responses (non-final-answer) never
 * have {@code finishReason} set unless the turn limit is reached. The loop
 * continues regardless of continuation requests for tool calls.
 * <li><b>Turn limit enforcement:</b> When turn limit is reached (on
 * non-final-answer responses), the response receives {@code finishReason=STOP}
 * to terminate the loop.
 * </ul>
 *
 * <h3>Expectations from upstream</h3>
 *
 * <ul>
 * <li>Prior processors may request continuation via
 * {@code RunUtils.getOrInitState().requestContinuation()}. This processor
 * consumes that flag and acts on it (side effect: flag is removed after read).
 * <li>Partial responses must be properly marked:
 * {@code response.partial().orElse(false)} must reliably indicate whether a
 * response is a streaming delta or final assembly.
 * <li>Final-answer responses are marked by
 * {@code ResponseUtils.isFinalAnswer(response)}, which examines the response
 * structure (not the continuation flag).
 * </ul>
 *
 * <h3>State Management</h3>
 *
 * <p>
 * Turn consumption is tracked in {@link com.agentengine.engine.utils.RunState}
 * (scoped to the current invocation). Only tool-call responses
 * (non-final-answer) consume a turn. The limit applies per run — it resets with
 * each new user invocation.
 */
public final class RunCompletionResponseProcessor implements ResponseProcessor {

  private final int maxSteps;

  public RunCompletionResponseProcessor(final Integer maxSteps) {
    this.maxSteps = maxSteps == null ? Integer.MAX_VALUE : maxSteps;
  }

  @Override
  public Single<ResponseProcessingResult> processResponse(final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return ResponseUtils.single(response.toBuilder().turnComplete(false).build());
    }
    final RunState state = RunUtils.getOrInitState(context);
    final boolean continuationRequested = state.consumeContinuation();
    final LlmResponse.Builder builder = response.toBuilder().turnComplete(true);
    if (ResponseUtils.isFinalAnswer(response)) {
      if (!continuationRequested) {
        builder.finishReason(response.finishReason().orElse(new FinishReason(FinishReason.Known.STOP)));
      }
    } else if (state.consumeTurn(maxSteps)) {
      builder.finishReason(response.finishReason().orElse(new FinishReason(FinishReason.Known.STOP)));
    }
    return ResponseUtils.single(builder.build());
  }
}
