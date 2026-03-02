package com.agentengine.engine.agents.processors.response;

import static com.agentengine.engine.utils.RunState.Phase.FINAL_ANSWER_DELIVERED;

import com.agentengine.engine.tools.ToolUtils;
import com.agentengine.engine.utils.RunState;
import com.agentengine.engine.utils.RunStateUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Optional;

/**
 * Enforces positive turn completion guarantees (Positive Guarantees).
 *
 * <p>Responsibilities:
 * - Force turnComplete=true if valid tool calls/responses are present in a non-partial response.
 * - Force turnComplete=true if the run has reached FINAL_ANSWER_DELIVERED phase.
 * - Force turnComplete=true if the model signals STOP and no negative enforcement occurred.
 *
 * <p>Ownership: state-driven turn completion.
 */
public final class TurnCompletionResponseProcessor implements ResponseProcessor {
  public static final TurnCompletionResponseProcessor INSTANCE = new TurnCompletionResponseProcessor();

  @Override
  public Single<ResponseProcessingResult> processResponse(
      final InvocationContext context, final LlmResponse response) {
    if (response.partial().orElse(false)) {
      return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
    }

    final RunState runState = RunStateUtils.getState(context);
    final boolean hasTools = ToolUtils.hasToolParts(response);
    final boolean isFinalAnswerDelivered = runState.phase() == FINAL_ANSWER_DELIVERED;
    final boolean modelStopped = response.finishReason()
        .map(fr -> fr.toString().contains("STOP"))
        .orElse(false);

    if (hasTools || isFinalAnswerDelivered || modelStopped) {
      if (response.turnComplete().isEmpty() || !response.turnComplete().get()) {
        final LlmResponse updated = response.toBuilder().turnComplete(true).build();
        return Single.just(ResponseProcessingResult.create(updated, List.of(), Optional.empty()));
      }
    }

    return Single.just(ResponseProcessingResult.create(response, List.of(), Optional.empty()));
  }
}
