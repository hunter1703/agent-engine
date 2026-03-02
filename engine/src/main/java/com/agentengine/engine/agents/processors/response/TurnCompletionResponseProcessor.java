package com.agentengine.engine.agents.processors.response;

 

import com.agentengine.engine.tools.ToolUtils;
import com.google.adk.agents.InvocationContext;
import com.google.adk.flows.llmflows.ResponseProcessor;
import com.google.adk.models.LlmResponse;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import java.util.Optional;
import com.google.genai.types.Content;

/**
 * Enforces positive turn completion guarantees (Positive Guarantees).
 *
 * <p>Responsibilities:
 * - Force turnComplete=true if valid tool calls/responses are present in a non-partial response.
 * - Force turnComplete=true if the run has reached FINISHED phase.
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

    final boolean hasTools = ToolUtils.hasToolParts(response);
    final boolean modelStopped = response.finishReason().isPresent();

    final boolean hasText = response.content()
        .flatMap(Content::parts)
        .stream()
        .flatMap(List::stream)
        .anyMatch(p -> p.text().isPresent() && !p.thought().orElse(false));

    // order is important
    return Single.just(ResponseProcessingResult.create(response.toBuilder().turnComplete(hasTools || hasText || modelStopped).build(), List.of(), Optional.empty()));
  }
}
