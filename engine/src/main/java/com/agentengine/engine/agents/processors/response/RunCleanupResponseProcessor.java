package com.agentengine.engine.agents.processors.response;

import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import com.google.genai.types.FinishReason;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Optional;

import static com.google.genai.types.FinishReason.Known.STOP;

/**
 * Finalizes and clears run state after delivery.
 *
 * <p>Responsibilities:
 * - Check if finished.
 * - Set finishReason=STOP if missing.
 * - Clear RunState from the session.
 *
 * <p>Ownership: run completion cleanup and termination signaling.
 */
public final class RunCleanupResponseProcessor implements ResponseProcessor {
  public static final RunCleanupResponseProcessor INSTANCE = new RunCleanupResponseProcessor();

  @Override
  public Single<ResponseProcessingResult> processResponse(
    final InvocationContext context, final LlmResponse response) {
    final boolean hasTools = ToolUtils.hasToolParts(response);
    final boolean isTurnComplete = response.turnComplete().orElse(false);
    final boolean isRunFinished = isTurnComplete && !hasTools;

    if (!isRunFinished) {
      return Single.just(ResponseProcessingResult.create(response.toBuilder().finishReason(Optional.empty()).build(), List.of(), Optional.empty()));
    }

    final LlmResponse updated = response.toBuilder().finishReason(response.finishReason().orElse(new FinishReason(STOP))).build();
    RunStateUtils.clearState(context);
    return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
  }
}
